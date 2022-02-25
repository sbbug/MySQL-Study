

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


###### 单机模式下增量插入数据并合并

    数据表创建
    CREATE TABLE alerts_test(
          tenant_id     UInt32,
          alert_id      String,
          timestamp     DateTime Codec(Delta, LZ4),
          alert_data    String,
          acked         UInt8 DEFAULT 0,
          ack_time      DateTime DEFAULT toDateTime(0),
          ack_user      LowCardinality(String) DEFAULT ''
    )
    ENGINE = ReplacingMergeTree(ack_time) 
    PARTITION BY tenant_id%5
    ORDER BY (tenant_id, timestamp, alert_id); 
    
    ReplacingMergeTree(ack_time)  // 数据合并，基于ack_time字段进行合并。关于ver字段，如果没有指定，保存最后一行，
    如果指定，保留版本最大的一行。
    PARTITION BY tenant_id // 单机内部数据分区方式 如果数据需要合并，必须位于同一个分区中
    // 比如基于tenant_id进行单机数据分区，同一个tenant_id下的报警记录可以通过acktime进行合并。
    // 需要使用哈希算法控制分区数量，比如将分区对5取模，得出五个分区。
    //也可以采取时间戳进行分区，比如partition by toYYYYMMDD(create_time)
    ORDER BY (tenant_id, timestamp, alert_id); // 三个字段建立索引，基于索引进行排序
    
    数据批量生成导入
    INSERT INTO alerts_test(tenant_id, alert_id, timestamp, alert_data)
    SELECT
      toUInt32(rand(1)%1000+1) AS tenant_id,
      randomPrintableASCII(64) as alert_id,
      toDateTime('2020-01-01 00:00:00') + rand(2)%(3600*24*30) as timestamp,
      randomPrintableASCII(1024) as alert_data
    FROM numbers(10000000);

    数据增量更新
    INSERT INTO alerts_test (tenant_id, alert_id, timestamp, alert_data, acked, ack_user, ack_time)
    SELECT 
      tenant_id, 
      alert_id, 
      timestamp,
      alert_data, 
      1 as acked, 
      concat('user', toString(rand()%1000)) as ack_user,       
      now() as ack_time
    FROM alerts WHERE cityHash64(alert_id) % 99 != 0;
    
    查看当前数据量
        select count(*) from alerts_test; // 未合并的数据量
        Query id: 781a50ed-934c-4c76-95be-b600317013b9
    
        ┌──count()─┐
        │ 19578073 │
        └──────────┘
    使用FINAL关键字后台合并
        select count(*) from alerts_test FINAL;
    
        Query id: aabbbb50-dfd9-4cfc-ba4f-3f685be06e32
        
        ┌──count()─┐
        │ 10000000 │
        └──────────┘
    
    强制做分区合并
    optimize table replac_merge_test FINAL
    时间会比较久，耐心等一下。
    
    合并完成后，后台不适用FINAL关键查询
        select count(*) from alerts_test;
        
        Query id: 004fbf72-832c-400c-8c81-fb8d5dfbd1d6
    
        ┌──count()─┐
        │ 10000000 │
        └──────────┘

###### 参考

1.https://juejin.cn/post/7039978768022110238
    

#### clickhouse集群


###### 查看集群信息：
   
    select * from clusters;
   
    ┌─cluster─────────┬─shard_num─┬─shard_weight─┬─replica_num─┬─host_name─┬─host_address─┬─port─┬─is_local─┬─user────┬─default_database─┬─errors_count─┬─estimated_recovery_time─┐
    │ default_cluster │         1 │            1 │           1 │ 9.0.16.4  │ 9.0.16.4     │ 9000 │        0 │ default │                  │            0 │                       0 │
    │ default_cluster │         2 │            1 │           1 │ 9.0.16.11 │ 9.0.16.11    │ 9000 │        1 │ default │                  │            0 │                       0 │
    │ default_cluster │         3 │            1 │           1 │ 9.0.16.17 │ 9.0.16.17    │ 9000 │        0 │ default │                  │            0 │                       0 │
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
                ENGINE = ReplicatedReplacingMergeTree('/clickhouse/tables/{layer}-{shard}/dis_test/alerts', '{replica}',ack_time) 
                PARTITION BY tenant_id%5 
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
                engine = Distributed(default_cluster, 'dis_test', 'alerts', tenant_id);
                
    分布式表数据批量导入
    INSERT INTO distri_alerts(tenant_id, alert_id, timestamp, alert_data)
                SELECT
                  toUInt32(rand(1)%1000+1) AS tenant_id,
                  randomPrintableASCII(64) as alert_id,
                  toDateTime('2020-01-01 00:00:00') + rand(2)%(3600*24*30) as timestamp,
                  randomPrintableASCII(1024) as alert_data
                FROM numbers(500000);
    
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
    3330836+3335148+3334026 = 1000w
    可以发现，数据被均衡的分片到对应的三个服务器上面，实现数据的分片存储。
    
    修改物理表存储索引 MergeTree -> ReplacingMergeTree
    
    
    先对表数据做增量修改
    
    INSERT INTO distri_alerts (tenant_id, alert_id, timestamp, alert_data, acked, ack_user, ack_time)
    SELECT tenant_id, alert_id, timestamp, alert_data, 
      1 as acked, 
      concat('user', toString(rand()%1000)) as ack_user,       now() as ack_time
    FROM distri_alerts WHERE cityHash64(alert_id) % 99 != 0;
    
    修改后查询每个分片的量
    1.
        ┌─count()─┐
        │ 4430476 │
        └─────────┘
    2.
        ┌─count()─┐
        │ 4433657 │
        └─────────┘
    3.
        ┌─count()─┐
        │ 4435292 │
        └─────────┘
    
    4430476+4433657+4435259 = 13299425
    通过分布式表查询结果：合并前，直接从count.txt中获取数据
        ┌──count()─┐
        │ 13299425 │
        └──────────┘
    通过分布式查询结果：FINAL关键子合并后，会全表扫描数据
        ┌──count()─┐
        │ 12201916 │
        └──────────┘
    查询数据
    
    发现数据查询结果不一致，理论上增量修改99%的数据，FINAL查询后数据量应该是1000w，而不是12201916
    
    SELECT count() FROM alerts FINAL WHERE NOT acked // 
    
    
    删除上述数据库，重新创建数据库，报如下错误：
    Received exception from server (version 21.3.9):
    Code: 342. DB::Exception: Received from 10.23.106.39:9000. DB::Exception: There was an error on [9.0.16.11:9000]: Code: 342, e.displayText() = DB::Exception: Existing table metadata in ZooKeeper differs in mode of merge operation. Stored in ZooKeeper: 0, local: 5 (version 21.3.9.83 (official build)). (METADATA_MISMATCH)
    
    再试一下好了，很奇怪。
    
    
    分布式表数据分区创建与合并，正确姿势尝试。
    
    创建分布式数据库
    CREATE DATABASE IF NOT EXISTS dis_test ON CLUSTER default_cluster; // 创建分布式数据库
    
    分布式物理表创建
    CREATE TABLE test_alerts on cluster default_cluster(
                  tenant_id     UInt32,
                  alert_id      String,
                  timestamp     DateTime Codec(Delta, LZ4),
                  alert_data    String,
                  acked         UInt8 DEFAULT 0,
                  ack_time      DateTime DEFAULT toDateTime(0),
                  ack_user      LowCardinality(String) DEFAULT ''
                )
                ENGINE = ReplicatedReplacingMergeTree('/clickhouse/tables/{layer}-{shard}/dis_test/test_alerts', '{replica}',ack_time) 
                PARTITION BY tenant_id%10 
                ORDER BY (tenant_id, timestamp, alert_id);
                
    // 每台机器数据表分十个分区
    // 分片服务器下，重复数据如何进行合并,添加ack_time字段
    
    
    分布式逻辑表创建
    CREATE TABLE dis_alerts ON CLUSTER default_cluster(
                  tenant_id     UInt32,
                  alert_id      String,
                  timestamp     DateTime Codec(Delta, LZ4),
                  alert_data    String,
                  acked         UInt8 DEFAULT 0,
                  ack_time      DateTime DEFAULT toDateTime(0),
                  ack_user      LowCardinality(String) DEFAULT ''
                )
                engine = Distributed(default_cluster, 'dis_test', 'test_alerts', tenant_id);
    // 集群环境下，使用tenant_id作为哈希ID，进行数据分片
    
    数据生成
    INSERT INTO dis_alerts(tenant_id, alert_id, timestamp, alert_data)
    SELECT
      toUInt32(rand(1)%1000+1) AS tenant_id,
      randomPrintableASCII(64) as alert_id,
      toDateTime('2020-01-01 00:00:00') + rand(2)%(3600*24*30) as timestamp,
      randomPrintableASCII(1024) as alert_data
    FROM numbers(10000000);
    
    
    数据增量修改
    INSERT INTO dis_alerts (tenant_id, alert_id, timestamp, alert_data, acked, ack_user, ack_time)
        SELECT 
          tenant_id, 
          alert_id, 
          timestamp,
          alert_data, 
          1 as acked, 
          concat('user', toString(rand()%1000)) as ack_user,       
          now() as ack_time
        FROM dis_alerts WHERE cityHash64(alert_id) % 99 != 0;
    
    普通方式查看分布式表数据
        select count(*) from dis_alerts;
        
        ┌──count()─┐
        │ 19047856 │
        └──────────┘
    
    合并方式查看分布式表数据
    
        select count(*) from dis_alerts FINAL;

        ┌──count()─┐
        │ 10000000 │
        └──────────┘
    
    后台强制执行合并
        optimize table replac_merge_test FINAL
        这种方式不支持？？
        Method optimize is not supported by storage Distributed. (NOT_IMPLEMENTED)
        正确方法
        optimize table dis_test.testt_alerts on cluster default_cluster FINAL;
        
    
    
    合并后普通查询


###### debugs

    每次删除分布式数据库，重新创建数据库，都会报如下错误
        Received exception from server (version 21.3.9):
        Code: 253. DB::Exception: Received from 10.23.106.39:9000. DB::Exception: There was an error on [9.0.16.11:9000]: Code: 253, e.displayText() = DB::Exception: Replica /clickhouse/tables/default-9.0.16.11/dis_test/test_alerts/replicas/9.0.16.11 already exists. (version 21.3.9.83 (official build)). (REPLICA_IS_ALREADY_EXIST)

    

###### 参考

     1.https://chowdera.com/2020/12/20201223001645445w.html
     2.https://help.aliyun.com/document_detail/167447.html?utm_content=g_1000230851&spm=5176.20966629.toubu.3.f2991ddcpxxvD1#p-lu3-qa5-p55

#### clickhouse数据操作


###### 更新操作
       clickhouse数据更新，比如delete update等属于异步操作，这个和mysql数据库区别较大。执行操作后，会有后台任务
       执行相关操作。

       关于单机环境下单表操作具体参考这里：https://juejin.cn/post/7039978768022110238

###### 参考

     1.https://juejin.cn/post/7039978768022110238
     2.https://www.cnblogs.com/traditional/p/15218743.html
     3.https://www.jianshu.com/p/dd38a6589394

#### springboot 连接clickhouse 连接池

    配置	缺省值	说明
    name	 	别名
    jdbcUrl	 	连接数据库的url，不同数据库不一样。例如： 
                mysql : jdbc:mysql://10.20.153.104:3306/druid2 
                oracle : jdbc:oracle:thin:@10.20.149.85:1521:ocnauto
    username	 	用户名
    password	 	密码。
    driverClassName	根据url自动识别这一项可配可不配，如果不配置druid会根据url自动识别dbType，然后选择相应的driverClassName
    initialSize	0	初始化时建立物理连接的个数。初始化发生在显示调用init方法，或者第一次getConnection时
    maxActive	8	最大连接池数量
    minIdle	 	最小连接池数量
    maxWait	 	获取连接时最大等待时间，单位毫秒。配置了maxWait之后，缺省启用公平锁，并发效率会有所下降，如果需要可以通过配置useUnfairLock属性为true使用非公平锁。
    
    ----------------------------------------------------------------------------------------------------------------
    poolPreparedStatements	false	
    maxOpenPreparedStatements	-1	
    validationQuery	 	用来检测连接是否有效的sql，要求是一个查询语句。如果validationQuery为null，testOnBorrow、testOnReturn、testWhileIdle都不会其作用。
    testOnBorrow	true	申请连接时执行validationQuery检测连接是否有效，做了这个配置会降低性能。
    testOnReturn	false	归还连接时执行validationQuery检测连接是否有效，做了这个配置会降低性能
    testWhileIdle	false	建议配置为true，不影响性能，并且保证安全性。申请连接的时候检测，如果空闲时间大于timeBetweenEvictionRunsMillis，执行validationQuery检测连接是否有效。
    timeBetweenEvictionRunsMillis	 	有两个含义： 
    1) Destroy线程会检测连接的间隔时间2) testWhileIdle的判断依据，详细看testWhileIdle属性的说明
    numTestsPerEvictionRun	 	不再使用，一个DruidDataSource只支持一个EvictionRun
    minEvictableIdleTimeMillis	 	 
    connectionInitSqls	 	物理连接初始化的时候执行的sql
    exceptionSorter	根据dbType自动识别	当数据库抛出一些不可恢复的异常时，抛弃连接
    filters	 	属性类型是字符串，通过别名的方式配置扩展插件，常用的插件有： 
    监控统计用的filter:stat日志用的filter:log4j防御sql注入的filter:wall
    proxyFilters	 	
    类型是List<com.alibaba.druid.filter.Filter>，如果同时配置了filters和proxyFilters，是组合关系，并非替换关系


###### 参考

     1.https://chowdera.com/2020/12/20201223001645445w.html. 
     2.https://help.aliyun.com/document_detail/167447.html?utm_content=g_1000230851&spm=5176.20966629.toubu.3.f2991ddcpxxvD1#p-lu3-qa5-p55


