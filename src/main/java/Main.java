import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.jeasy.random.EasyRandom;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

@Slf4j
public class Main {

    private static final EasyRandom EASY_RANDOM = new EasyRandom();
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(NON_NULL)
            .configure(FAIL_ON_EMPTY_BEANS, false)
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(WRITE_DATES_AS_TIMESTAMPS, false)
            .registerModule(new JavaTimeModule());

    public static void main(String[] args) {


        CrptApi.ApiConfig apiConfig = CrptApi.ApiConfig
                .builder()
                .baseUrl("https://ismp.crpt.ru/api/v3")
                .connectTimeout(5000)
                .readTimeout(5000)
                .rateLimit(new CrptApi.RateLimit(TimeUnit.SECONDS, 1))
                .build();
        CrptApi api = new CrptApi(apiConfig, MAPPER);

        List<CrptApi.Document> requests = IntStream.range(0, 10)
                .mapToObj(index -> EASY_RANDOM.nextObject(CrptApi.Document.class))
                .collect(Collectors.toList());

        requests.forEach(request -> {
            Thread thread = new Thread(() -> {
                try {
                    api.createDocument(request, EASY_RANDOM.nextObject(String.class));
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            });
            thread.start();
        });
    }
}
