

## clickhouse基础

#### 配置可视化操作客户端

     通过配置可视化客户端实现clickHouse操作
    
     1.
        docker pull spoonest/clickhouse-tabix-web-client
     2.
        docker run -d -p 8080:80 spoonest/clickhouse-tabix-web-client
     3.
        vi /etc/clickhouse-server/config.xml  # 取消 <listen_host>::</listen_host>的注释
        systemctl stop firewalld   # 关闭防火墙


[tabix安装入门](https://www.jianshu.com/p/b0185d071c6c)

#### 客户端登陆clickhouse

    clickhouse-client -h ip --user ... --password ...

#### clickhouse 单机

###### DDL DML

    数据删除，两种方式，删除整张表或者alter table table_name delete where ...
    
    查看表结构: desc table table_name
    查看建表语句:show create table_name

    clickhouse数据更新，比如delete update等属于异步操作，这个和mysql数据库区别较大。执行操作后，会有后台任务
    执行相关操作。
        
    关于单机环境下单表操作具体参考这里：https://juejin.cn/post/7039978768022110238

###### 参考

1.https://juejin.cn/post/7039978768022110238
2.https://www.cnblogs.com/traditional/p/15218743.html  
3.https://www.jianshu.com/p/dd38a6589394

#### clickhouse集群


###### 查看集群信息：

    select * from clusters;
   
    ┌─cluster─────────┬─shard_num─┬─shard_weight─┬─replica_num─┬─host_name─┬─host_address─┬─port─┬─is_local─┬─user────┬─default_database─┬─errors_count─┬─estimated_recovery_time─┐
    │ default_cluster │         1 │            1 │           1 │ 9.0.16.*  │ 9.0.16.*     │ 9000 │        0 │ default │                  │            0 │                       0 │
    │ default_cluster │         2 │            1 │           1 │ 9.0.16.* │ 9.0.16.*    │ 9000 │        1 │ default │                  │            0 │                       0 │
    │ default_cluster │         3 │            1 │           1 │ 9.0.16.* │ 9.0.16.*    │ 9000 │        0 │ default │                  │            0 │                       0 │
    └─────────────────┴───────────┴──────────────┴─────────────┴───────────┴──────────────┴──────┴──────────┴─────────┴──────────────────┴──────────────┴─────────────────────────┘
    
    shard_num:集群的分片数 从1开始
    shard_weight:写数据时，分片的相对权重
    replica_num:该master节点的副本数量
    is_local:是否为本机

###### 集群数据表存储原理

    分布式表分为物理表与逻辑表，物理表存储实实在在的数据，逻辑表相当于视图。
    分布式查询时，通过逻辑表作为入口，实现对分片集群上数据的聚合获取。
    clickhouse分布式是以表为基础单位实现的分片。

###### 分布式表创建过程
    分布式表创建：
    CREATE DATABASE IF NOT EXISTS dis_test ON CLUSTER default_cluster; // 创建分布式数据库
    drop database dis_test on cluster default_cluster; // 删除分布式数据库
    
    use dis_test // 每次命令行操作之前，一定要记得use database
    
    分布式物理表创建
    CREATE TABLE alerts on cluster default_cluster(
                  tenant_id     UInt32,
                  alert_id      String,
                  timestamp     DateTime Codec(Delta, LZ4),
                  alert_data    String,
                  acked         UInt8 DEFAULT 0,
                  ack_time      DateTime DEFAULT toDateTime(0),
                  ack_user      LowCardinality(String) DEFAULT ''
                )
                ENGINE = ReplicatedReplacingMergeTree('/clickhouse/tables/{layer}-{shard}/dis_test/alerts', '{replica}') 
                PARTITION BY tuple() 
                ORDER BY (tenant_id, timestamp, alert_id);
                
    分布式逻辑表创建，创建一个逻辑视图
    CREATE TABLE distri_alerts ON CLUSTER default_cluster(
                  tenant_id     UInt32,
                  alert_id      String,
                  timestamp     DateTime Codec(Delta, LZ4),
                  alert_data    String,
                  acked         UInt8 DEFAULT 0,
                  ack_time      DateTime DEFAULT toDateTime(0),
                  ack_user      LowCardinality(String) DEFAULT ''
                )
                engine = Distributed(default_cluster, 'dis_test', 'alerts', rand());
                
    分布式表数据批量导入，测试使用
    INSERT INTO distri_alerts(tenant_id, alert_id, timestamp, alert_data)
                SELECT
                  toUInt32(rand(1)%1000+1) AS tenant_id,
                  randomPrintableASCII(64) as alert_id,
                  toDateTime('2020-01-01 00:00:00') + rand(2)%(3600*24*30) as timestamp,
                  randomPrintableASCII(1024) as alert_data
                FROM numbers(10000000);
    
    验证每个分片服务器分别导入了多少数据。
    登陆每一台机器，分别执行下面语句，查看本地表数据量。
    select count(*) from alerts;
    1.
        ┌─count()─┐
        │ 3330836 │
        └─────────┘
    2.
        ┌─count()─┐
        │ 3335148 │
        └─────────┘
    3.  
        ┌─count()─┐
        │ 3334026 │
        └─────────┘
    可以发现，数据被均衡的分片到对应的三个服务器上面，实现数据的分片存储。
    
    修改物理表存储索引 MergeTree -> ReplacingMergeTree
    
    
    对表这数据做增量修改
    
    INSERT INTO distri_alerts (tenant_id, alert_id, timestamp, alert_data, acked, ack_user, ack_time)
    SELECT tenant_id, alert_id, timestamp, alert_data, 
      1 as acked, 
      concat('user', toString(rand()%1000)) as ack_user,       now() as ack_time
    FROM alerts WHERE cityHash64(alert_id) % 99 != 0;
    
    修改后查询每个分片的量
    1.
       ...
    2.
       ...
    3.
       ...
    
    查询数据
    
    SELECT count() FROM alerts FINAL WHERE NOT acked //执行SQL语句做条件查询 
    // 使用FINAL关键字后，clickhouse查询后会对结果做合并
    
    
    删除上述数据库，重新创建数据库，报如下错误：
    Received exception from server (version 21.3.9):
    Code: 342. DB::Exception: Received from 10.23.106.39:9000. DB::Exception: There was an error on [9.0.16.11:9000]: Code: 342, e.displayText() = DB::Exception: Existing table metadata in ZooKeeper differs in mode of merge operation. Stored in ZooKeeper: 0, local: 5 (version 21.3.9.83 (official build)). (METADATA_MISMATCH)
    
    再试一下好了，很奇怪。
    
    采取上述数据，在集群环境下对数据做增量修改时，前后查询获取的数据量不一致。

###### 参考

1.https://chowdera.com/2020/12/20201223001645445w.html
2.https://help.aliyun.com/document_detail/167447.html?utm_content=g_1000230851&spm=5176.20966629.toubu.3.f2991ddcpxxvD1#p-lu3-qa5-p55


