package dev.obsidra.briefbot.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.net.Proxy;

import okhttp3.OkHttpClient;

import org.junit.jupiter.api.Test;

class TelegramConfigTest {

    @Test
    void noProxyMeansDirectConnection() {
        assertThat(TelegramConfig.httpClientCreator(null).get().proxy()).isNull();
        assertThat(TelegramConfig.httpClientCreator("  ").get().proxy()).isNull();
    }

    @Test
    void httpProxyIsApplied() {
        OkHttpClient client = TelegramConfig.httpClientCreator("http://127.0.0.1:2080").get();

        assertThat(client.proxy().type()).isEqualTo(Proxy.Type.HTTP);
        InetSocketAddress address = (InetSocketAddress) client.proxy().address();
        assertThat(address.getHostString()).isEqualTo("127.0.0.1");
        assertThat(address.getPort()).isEqualTo(2080);
    }

    @Test
    void socksProxyIsApplied() {
        OkHttpClient client = TelegramConfig.httpClientCreator("socks5://127.0.0.1:1080").get();

        assertThat(client.proxy().type()).isEqualTo(Proxy.Type.SOCKS);
    }

    @Test
    void unsupportedProxyIsRejectedAtStartup() {
        assertThatThrownBy(() -> TelegramConfig.httpClientCreator("ftp://127.0.0.1:21"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bot.proxy");
        assertThatThrownBy(() -> TelegramConfig.httpClientCreator("127.0.0.1:2080"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
