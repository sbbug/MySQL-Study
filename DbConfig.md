
## MYSQL常用超参数（8.0）

    线上MySQL服务性能调优，需要根据具体业务场景，选择合适的参数设置，进行调优实验。

    port=3306  // MySQL进程默认运行端口号

    atadir = ... // 数据文档存储路径

    skip-slave-start // 禁止slave复制进程随MySQL启动

    max_connections = 1000 // MySQL允许的最大进程连接数

    tmp_table_size = 32M // MySQL临时表最大内存

    show_query_log = 0 // 是否开启慢查询记录
    show_query_log_file // 日志输出路径
    long_query_time  // 慢查询时间阈值

    log-error = mysql-error.log // 错误日志位置

    binlog_expire_logs_seconds = 2592000 // binlog日志过期时间

    transaction_isolation: // 事务隔离级别设置
        READ-UNCOMMITTEDREAD // 读未提交
        COMMITTED // 读提交
        REPEATABLE // 可重复读
        READSERIALIZABLE // 串行化
    
    rpl_semi_sync_master_enabled // 是否开启半同步
    rpl_semi_sync_master_timeout // master收到slave同步后的ack超时时间
    rpl_semi_sync_master_wait_for_slave_count // 半同步节点数量

    replica_parallel_type  // 从节点并行复制relay log日志类型
        database
        clock
    replica_parallel_worker // 并行复制线程数

    sync_binlog // binlog日志落盘方式
      sync_binlog = 0 // binglog日志采取异步落盘方式，即借助操作系统刷缓存日志到磁盘，如果power fail或者crash，会造成
      已经提交的事务丢失
      sync_binlog = 1 // 每次事务提交之前都做异步落盘操作，在断电或者crash情况，可以保证数据丢失最少，只有把将要写入磁盘的数据
                        丢失。增加了磁盘写入量，影响系统性能
      sync_binlog = N // 当追加了N个binlog commit,在执行落盘操作
      推荐配置：sync_binlog = 1 innodb_flush_log_at_trx_commit = 1配置

    innodb_flush_log_at_trx_commit 
        innodb_flush_log_at_trx_commit = 0 // 每次事务提交都会将日志刷到磁盘
        innodb_flush_log_at_trx_commit = 1 // 每秒一次将日志落盘
        innodb_flush_log_at_trx_commit = 2
    




## 参考

1、[MySQL手册](https://dev.mysql.com/doc/refman/8.0/en/server-option-variable-reference.html)