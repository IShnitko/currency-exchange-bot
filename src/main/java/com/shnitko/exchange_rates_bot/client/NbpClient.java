package com.shnitko.exchange_rates_bot.client;

import com.shnitko.exchange_rates_bot.exception.ServiceException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Component
public class NbpClient {

    @Autowired
    private OkHttpClient client;

    @Value("${nbp.currency.rates.xml.url}")
    private String url;

    public String getCurrencyRatesXML() throws ServiceException {
        var request = new Request.Builder()
                .url(url)
                .addHeader("Accept", "application/xml")  // Explicitly request XML
                .build();

        try (var response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new ServiceException("Unexpected response: " + response.code());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new ServiceException("Empty response body");
            }

            // Handle BOM and encoding issues
            BufferedSource source = body.source();
            source.request(Long.MAX_VALUE);
            Buffer buffer = source.getBuffer();
            Charset charset = StandardCharsets.UTF_8;

            // Remove UTF-8 BOM if present
            if (buffer.size() > 3 &&
                    buffer.getByte(0) == (byte) 0xEF &&
                    buffer.getByte(1) == (byte) 0xBB &&
                    buffer.getByte(2) == (byte) 0xBF) {
                buffer.skip(3);
            }

            return buffer.clone().readString(charset);
        } catch (IOException e) {
            throw new ServiceException("Error while getting rates", e);
        }
    }
}
