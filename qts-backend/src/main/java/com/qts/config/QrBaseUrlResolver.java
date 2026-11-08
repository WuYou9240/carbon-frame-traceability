package com.qts.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * 二维码跳转地址解析器。
 *
 * 二维码是拿手机扫的，地址里必须是「手机能访问到的局域网 IP」，不能是 localhost。
 * 而本机 IP 会随网络环境变化（换个教室 WiFi 就变），所以默认配置为 auto：
 * 启动时探测默认路由所在网卡的 IP，再拼上当前服务端口。
 * 确需固定地址时，用环境变量 QR_BASE_URL 覆盖。
 */
@Slf4j
@Component
public class QrBaseUrlResolver {

    private final String configured;
    private final String port;
    private volatile String cached;

    public QrBaseUrlResolver(@Value("${app.qr-base-url:auto}") String configured,
                             @Value("${server.port:8080}") String port) {
        this.configured = configured;
        this.port = port;
    }

    public String base() {
        if (cached != null) {
            return cached;
        }
        if (configured != null && !configured.isBlank() && !"auto".equalsIgnoreCase(configured.trim())) {
            cached = configured.endsWith("/") ? configured : configured + "/";
        } else {
            cached = "http://" + detectLanIp() + ":" + port + "/#/trace/";
            log.info("扫码基地址自动探测为 {}", cached);
        }
        return cached;
    }

    /** 取默认路由所在网卡的 IPv4。UDP connect 不实际发包，只是让系统按路由表选定出口网卡 */
    private String detectLanIp() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 80);
            String ip = socket.getLocalAddress().getHostAddress();
            if (ip != null && !ip.startsWith("127.")) {
                return ip;
            }
        } catch (Exception e) {
            log.warn("自动探测本机局域网 IP 失败，退回 localhost：{}", e.getMessage());
        }
        return "localhost";
    }
}
