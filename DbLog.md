
## MySQL 日志

    MySQL数据库中经常有各种日志出现，比如binlog、undo log 、redo log 、relay log、error-log等，他们都是怎么实现的呢，数据结构
    长什么样子？什么时候被触发写入？都有什么作用呢？我们平时在做项目时如何借鉴他们的思想呢？

### binlog
    binlog日志主要是为主从节点数据同步使用的
#### binlog event
    binlog 事件类型主要有binlog management,statement based replication events,row based replication event,
    load refile replication event.
    binglog management:
                      start_event stop_event  主要实现binlog日志管理
    statement based replication event:
                      rows_event,主要包括delete_rows_event,write_rows_event,update_rows_event.
    load refile replication event:
                      发生载入xml或者SQL执行文件等信息，作为一种binlog事件

    mysql中将数据库中数据表数据变更事件抽象成binlog事件进行管理，每种事件分配对应的事件类型。
    
    主从同步主要涉及这三个事件：table_map_event(表结构变更),rows_event,rows_query_event


### undo log
 
    回滚日志，只存在于innodb存储引擎中。数据库每一行会多余分配一个undo log日志的指针，用来指向历史版本链。undo log会在每次事务执行的过程中产生，即事务未提交时，
    undo 日志已经产生，并链接到日志版本链中。考虑几种场景，比如开启事务，执行某个操作，执行过程遇到问题，需要进行回滚，那就就会根据当前记录行的undo log
    指针寻找最近一次修改的undo log，进行回滚操作。再比如，对于多事务并发操作，有另一个事务需要读取当前行记录的数据，但是行记录被当前事务锁住了，获取不到，此时会
    根据undo log的指针找到最近一条记录，进行读取。

    undo日志是一种临时日志，和当前事务紧密关联，记录着一个事务的操作轨迹。undo日志一般不会被持久化。

### redo log
    
    考虑一种场景，MySQL服务器正在处理事务请求，此时有部分事务还未提交，突然宕机了，这个时候该怎么办呢？
    服务器即使重启了，但内存的数据会丢失，我们知道MySQL为了不影响性能，并没有将undo日志进行持久化。所以这时引入另一种
    格式的日志，叫redo log。
    
    MySQL执行语句，在提交之前会将事务语句写入到redo log中，如果此时突然宕机，服务重启后，会从redo do恢复，然后继续执行事务操作。

### relay log
    
    relay是中继，起到接力作用。主要用在主从节点同步之间。从节点收到Binlog日志后，会把binlog日志写到relay log中，然后从节点开启
    SQL执行线程，从relay log中读取SQL语句执行。

### error log

    记录MySQL运行过程中的错误日志，方便线上排查问题

### 慢查询日志


## 参考

    1、https://dev.mysql.com/doc/refman/5.7/en/innodb-redo-log.html
    2、https://dev.mysql.com/doc/refman/5.7/en/replica-logs-relaylog.html