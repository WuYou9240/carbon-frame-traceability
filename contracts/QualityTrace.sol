// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

/**
 * @title QualityTrace 车架质量哈希存证合约
 * @notice 维护 车架序列号 => 关键质量信息SHA-256哈希 的映射关系。
 *         质检完成后由后端调用 recordHash 登记；扫码验真时由后端调用 getHash 比对。
 */
contract QualityTrace {

    struct Record {
        bytes32 hashValue;    // 关键质量数据 SHA-256 哈希
        string  dataVersion;  // 数据版本号（防中途篡改重录）
        address operator;     // 登记操作人（链上账户地址）
        uint256 timestamp;    // 登记时间戳
    }

    // 序列号 => 存证记录
    mapping(string => Record) private records;
    // 已登记的序列号列表
    string[] private serialList;

    event HashRecorded(string indexed serialNumber, bytes32 hashValue, address operator, uint256 timestamp);

    // 注：联盟链本身通过节点准入控制写入权限，合约层不再重复做账户白名单

    /**
     * @notice 登记车架关键质量数据哈希（质检完成后调用）
     * @dev 首次登记新建存证；数据修正后可再次登记形成新版本，
     *      历史版本可通过 HashRecorded 事件审计追溯
     */
    function recordHash(string memory serialNumber, bytes32 hashValue, string memory dataVersion) public {
        bool isNew = records[serialNumber].hashValue == bytes32(0);
        records[serialNumber] = Record(hashValue, dataVersion, msg.sender, block.timestamp);
        if (isNew) {
            serialList.push(serialNumber);
        }
        emit HashRecorded(serialNumber, hashValue, msg.sender, block.timestamp);
    }

    /**
     * @notice 查询车架已存证的哈希值（验真比对用）
     */
    function getHash(string memory serialNumber) public view returns (bytes32) {
        return records[serialNumber].hashValue;
    }

    /**
     * @notice 查询完整存证记录
     */
    function getRecord(string memory serialNumber) public view
        returns (bytes32 hashValue, string memory dataVersion, address operator, uint256 timestamp) {
        Record memory r = records[serialNumber];
        return (r.hashValue, r.dataVersion, r.operator, r.timestamp);
    }

    function isRecorded(string memory serialNumber) public view returns (bool) {
        return records[serialNumber].hashValue != bytes32(0);
    }

    function totalRecords() public view returns (uint256) {
        return serialList.length;
    }

    function getSerialByIndex(uint256 index) public view returns (string memory) {
        require(index < serialList.length, "index out of range");
        return serialList[index];
    }
}
