package com.qts;

import com.qts.config.QrBaseUrlResolver;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan("com.qts.mapper")
@EnableAsync
public class QtsApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(QtsApplication.class, args);

        // 端口从运行环境读取：之前这里写死 8090，用 --server.port 改端口后横幅会显示错误端口，
        // 演示时容易照着横幅去连错误地址。
        String port = ctx.getEnvironment().getProperty("server.port", "8080");
        String qrBase = ctx.getBean(QrBaseUrlResolver.class).base();
        System.out.printf("""
                ============================================================
                  公路自行车碳纤维车架质量溯源系统 启动成功
                  系统入口:     http://localhost:%s/
                  后端接口前缀: http://localhost:%s/api
                  扫码溯源前缀: %s{序列号}
                ============================================================%n""", port, port, qrBase);
    }
}
