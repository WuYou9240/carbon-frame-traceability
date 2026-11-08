package com.qts.config;

import org.fisco.bcos.sdk.v3.BcosSDK;
import org.fisco.bcos.sdk.v3.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

@Configuration
public class FiscoBcosConfig {

    private static final Logger log = LoggerFactory.getLogger(FiscoBcosConfig.class);

    /** 链节点主机，来自 application.yml 的 fisco.peer-host（可用环境变量 FISCO_PEER_HOST 覆盖） */
    @Value("${fisco.peer-host:127.0.0.1}")
    private String peerHost;

    @Bean
    public BcosSDK bcosSDK() throws Exception {
        // SDK 的 config.toml 仅支持文件系统路径，
        // 因此先把 classpath 内的证书与配置解压到临时目录
        Path sdkDir = Files.createTempDirectory("fisco-sdk-");
        Path confDir = Files.createDirectories(sdkDir.resolve("conf"));
        extractResource("conf/ca.crt", confDir.resolve("ca.crt"));
        extractResource("conf/sdk.crt", confDir.resolve("sdk.crt"));
        extractResource("conf/sdk.key", confDir.resolve("sdk.key"));

        String toml = new String(new ClassPathResource("config.toml").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        String confAbs = confDir.toAbsolutePath().toString().replace('\\', '/');
        toml = toml.replace("certPath = \"conf\"", "certPath = \"" + confAbs + "\"");

        // peers 里的主机地址按 fisco.peer-host 重写，端口原样保留：
        // 虚拟机为 DHCP，IP 一变只需改环境变量 FISCO_PEER_HOST，不用动 config.toml
        String rewritten = Pattern.compile("\"\\d+\\.\\d+\\.\\d+\\.\\d+:(\\d+)\"")
                .matcher(toml)
                .replaceAll("\"" + peerHost + ":$1\"");
        if (!rewritten.equals(toml)) {
            log.info("FISCO BCOS 节点地址已重写为: {}", peerHost);
            toml = rewritten;
        }
        Path configFile = sdkDir.resolve("config.toml");
        Files.write(configFile, toml.getBytes(StandardCharsets.UTF_8));
        log.info("FISCO BCOS SDK 配置文件: {}", configFile);

        return BcosSDK.build(configFile.toAbsolutePath().toString());
    }

    @Bean
    public Client client(BcosSDK bcosSDK) {
        // 群组 group0（见节点 config.genesis 中的 group_id）
        return bcosSDK.getClient("group0");
    }

    private void extractResource(String resourcePath, Path target) throws Exception {
        FileCopyUtils.copy(new ClassPathResource(resourcePath).getInputStream(),
                Files.newOutputStream(target));
    }
}
