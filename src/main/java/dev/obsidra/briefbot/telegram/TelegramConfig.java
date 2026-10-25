package dev.obsidra.briefbot.telegram;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.function.Supplier;

import okhttp3.Authenticator;
import okhttp3.OkHttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.TelegramOkHttpClientFactory;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import dev.obsidra.briefbot.config.BotProperties;

@Configuration
public class TelegramConfig {

    /** Один HTTP-клиент и для long polling, и для отправки сообщений — с прокси, если он задан. */
    @Bean
    OkHttpClient telegramHttpClient(BotProperties props) {
        return httpClientCreator(props.proxy()).get();
    }

    @Bean
    TelegramClient telegramClient(BotProperties props, OkHttpClient telegramHttpClient) {
        return new OkHttpTelegramClient(telegramHttpClient, props.token());
    }

    /** Фабрики библиотеки уже настроены под long polling (таймауты, пул соединений); мы только выбираем прокси. */
    static Supplier<OkHttpClient> httpClientCreator(String proxyUrl) {
        if (proxyUrl == null || proxyUrl.isBlank()) {
            return new TelegramOkHttpClientFactory.DefaultOkHttpClientCreator();
        }
        URI uri = URI.create(proxyUrl.strip());
        if (uri.getHost() == null || uri.getPort() < 0) {
            throw new IllegalArgumentException("bot.proxy: ожидается адрес вида http://127.0.0.1:2080, получено " + proxyUrl);
        }
        InetSocketAddress address = InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort());
        return switch (String.valueOf(uri.getScheme()).toLowerCase()) {
            case "http" -> new TelegramOkHttpClientFactory.HttpProxyOkHttpClientCreator(
                    () -> new Proxy(Proxy.Type.HTTP, address), () -> Authenticator.NONE);
            case "socks", "socks5" -> new TelegramOkHttpClientFactory.SocksProxyOkHttpClientCreator(
                    () -> new Proxy(Proxy.Type.SOCKS, address));
            default -> throw new IllegalArgumentException(
                    "bot.proxy: поддерживаются http:// и socks5://, получено " + proxyUrl);
        };
    }
}
