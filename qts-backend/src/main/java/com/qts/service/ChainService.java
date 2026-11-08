package com.qts.service;

import com.qts.common.BizException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.v3.transaction.manager.AssembleTransactionProcessor;
import org.fisco.bcos.sdk.v3.transaction.manager.TransactionProcessorFactory;
import org.fisco.bcos.sdk.v3.transaction.model.dto.CallResponse;
import org.fisco.bcos.sdk.v3.transaction.model.dto.TransactionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * 区块链上链存证服务：封装 QualityTrace 合约的 recordHash / getHash 调用
 */
@Slf4j
@Service
public class ChainService {

    private final Client client;

    @Value("${fisco.contract-address}")
    private String contractAddress;

    private CryptoKeyPair keyPair;
    private AssembleTransactionProcessor txProcessor;
    private String abi;

    public ChainService(Client client) {
        this.client = client;
    }

    @PostConstruct
    public void init() throws Exception {
        keyPair = client.getCryptoSuite().getCryptoKeyPair();
        log.info("链上交易账户地址: {}", keyPair.getAddress());

        abi = StreamUtils.copyToString(
                new ClassPathResource("QualityTrace.abi").getInputStream(), StandardCharsets.UTF_8);

        txProcessor = TransactionProcessorFactory.createAssembleTransactionProcessor(client, keyPair);
        log.info("QualityTrace 合约地址: {}", contractAddress);
    }

    public String accountAddress() {
        return keyPair.getAddress();
    }

    /** 当前使用的合约地址（取自 application.yml 的 fisco.contract-address） */
    public String contractAddress() {
        return contractAddress;
    }

    /** 当前区块高度 */
    public BigInteger blockNumber() {
        return client.getBlockNumber().getBlockNumber();
    }

    /**
     * 登记关键质量数据哈希上链
     *
     * @param serialNumber 车架序列号
     * @param sha256Hex    64 位十六进制 SHA-256（不带 0x 前缀）
     * @param dataVersion  数据版本
     * @return 交易回执信息
     */
    public TxResult recordHash(String serialNumber, String sha256Hex, String dataVersion) throws Exception {
        // bytes32 参数需传 byte[32]
        byte[] hashBytes = hexToBytes(sha256Hex);
        TransactionResponse response = txProcessor.sendTransactionAndGetResponse(
                contractAddress, abi, "recordHash",
                List.of(serialNumber, hashBytes, dataVersion));
        if (response.getTransactionReceipt() == null || !response.getTransactionReceipt().isStatusOK()) {
            String detail = response.getTransactionReceipt() != null
                    ? "receiptStatus=" + response.getTransactionReceipt().getStatus()
                      + ", receiptMsg=" + response.getTransactionReceipt().getMessage()
                      + ", output=" + response.getTransactionReceipt().getOutput()
                    : "returnMessage=" + response.getReturnMessage();
            log.warn("上链交易失败 serial={} detail={}", serialNumber, detail);
            throw new BizException("上链交易失败: " + detail);
        }
        var receipt = response.getTransactionReceipt();
        TxResult r = new TxResult();
        r.txHash = receipt.getTransactionHash();
        r.blockNumber = receipt.getBlockNumber() != null
                ? receipt.getBlockNumber().longValue() : null;
        r.operator = keyPair.getAddress();
        log.info("上链成功 serial={} tx={} block={}", serialNumber, r.txHash, r.blockNumber);
        return r;
    }

    /** 查询链上存证哈希；未登记返回 null */
    public String getHash(String serialNumber) throws Exception {
        CallResponse response = txProcessor.sendCall(
                keyPair.getAddress(), contractAddress, abi, "getHash",
                Collections.singletonList(serialNumber));
        if (response.getReturnCode() != 0) {
            throw new BizException("链上查询失败: " + response.getReturnMessage());
        }
        List<Object> values = response.getReturnObject();
        if (values == null || values.isEmpty()) {
            return null;
        }
        Object v = values.get(0);
        String hex;
        if (v instanceof byte[] bytes) {
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            hex = sb.toString();
        } else {
            hex = v.toString();
            hex = hex.startsWith("hex://") ? hex.substring(6) : hex;
        }
        hex = hex.replaceFirst("^0[xX]", "");
        if (hex.matches("^0+$") || hex.isEmpty()) {
            return null;
        }
        return hex;
    }

    public boolean isRecorded(String serialNumber) throws Exception {
        return getHash(serialNumber) != null;
    }

    private static byte[] hexToBytes(String hex) {
        hex = hex.replaceFirst("^0[xX]", "");
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    public static class TxResult {
        public String txHash;
        public Long blockNumber;
        public String operator;
    }
}
