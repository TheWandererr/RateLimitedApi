import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newScheduledThreadPool;

public class CrptApi {

    private final Invoker invoker;

    public CrptApi(ApiConfig apiConfig, ObjectMapper mapper) {
        requireNonNull(apiConfig);
        RateLimit rateLimit = apiConfig.getRateLimit();
        this.invoker = rateLimit == null
                ? new Invoker(apiConfig, mapper)
                : new RateLimitedInvoker(apiConfig, mapper, rateLimit);
    }

    /**
     * <p>
     * * Note: There is no information about signature provided inside test task
     * * </p>
     */
    public DocumentCreatedResponse createDocument(Document document, String signature) throws IOException {
        return invoker.invokePost(document, Constants.RestURIs.CREATE_DOCUMENT, DocumentCreatedResponse.class);
    }

    @Slf4j
    public static class Invoker {

        protected final String baseUrl;
        protected final OkHttpClient client;
        protected final ObjectMapper mapper;

        public Invoker(ApiConfig apiConfig, ObjectMapper mapper) {
            this.baseUrl = apiConfig.getBaseUrl();
            this.client = new OkHttpClient.Builder()
                    .connectTimeout(Duration.ofMillis(apiConfig.getConnectTimeout()))
                    .readTimeout(Duration.ofMillis(apiConfig.getReadTimeout()))
                    .retryOnConnectionFailure(true)
                    .build();
            this.mapper = mapper;
        }

        public <T> T invokePost(Object body, String uri, Class<T> responseType) throws IOException {
            String json = toJson(body);
            RequestBody requestBody = RequestBody.create(json, Constants.MediaTypes.JSON);
            Request request = new Request.Builder()
                    .url(baseUrl + uri)
                    .post(requestBody)
                    .build();
            return invoke(request, responseType);
        }

        private String toJson(Object object) throws IOException {
            return mapper.writeValueAsString(object);
        }

        public <T> T invoke(Request request, Class<T> responseType) throws IOException {
            Call call = client.newCall(request);
            log.info("Performing request: {}", request);
            try (Response response = call.execute()) {
                ResponseBody body = response.body();
                String json = body.string();
                log.info("Retrieved response : {}", json);
                return mapper.readValue(json, responseType);
            }
        }
    }

    @Slf4j
    public static class RateLimitedInvoker extends Invoker {

        private final long initialQuota;
        private final AtomicLong quota;

        public RateLimitedInvoker(ApiConfig apiConfig, ObjectMapper mapper, RateLimit rateLimit) {
            super(apiConfig, mapper);
            this.initialQuota = rateLimit.amount;
            this.quota = new AtomicLong(rateLimit.amount);
            ScheduledExecutorService scheduler = newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(this::resetQuota, 0, 1, rateLimit.unit);
        }

        private void resetQuota() {
            quota.set(initialQuota);
        }

        private boolean isInvocable() {
            long currentQuota = quota.updateAndGet(val -> val >= 0 ? val - 1 : val);
            return currentQuota >= 0;
        }

        @Override
        public <T> T invoke(Request request, Class<T> responseType) throws IOException {
            while (!isInvocable()) {
                // Блокируем вызов согласно постановке
            }
            return super.invoke(request, responseType);
        }
    }

    @Builder
    @Getter
    public static class ApiConfig {

        private RateLimit rateLimit;
        private String baseUrl;
        private int connectTimeout;
        private int readTimeout;
    }

    @Getter
    public static class RateLimit {

        private final TimeUnit unit;
        private final long amount;

        public RateLimit(TimeUnit timeUnit, int requests) {
            if (requests <= 0) {
                throw new IllegalArgumentException("Amount must be positive");
            }
            this.unit = timeUnit;
            this.amount = requests;
        }
    }

    @UtilityClass
    public static class Constants {

        public static class RestURIs {

            public static final String CREATE_DOCUMENT = "/lk/documents/create";
        }

        public static class MediaTypes {

            public static final MediaType JSON = MediaType.parse("application/json");
        }

    }

    @Builder
    @Getter
    public static class Document {

        private Description description;
        @JsonProperty("doc_id")
        private String id;
        @JsonProperty("producer_inn")
        private String producerInn;

        @Builder
        @Getter
        public static class Description {

            private String participantInn;
        }

        // other fields there
    }

    public static class BaseResponse {

        private Error error;

        @Builder
        public static class Error {

            private final String code;
            private final String message;
        }
    }

    public static class DocumentCreatedResponse extends BaseResponse {

        // Required fields here
    }
}
