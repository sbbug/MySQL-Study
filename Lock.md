

## Mysql中锁的实现
    
    针对InnoDB存储引擎


#### 互斥锁和共享锁
    
    宏观概念，MySQL中的大部分锁在都分为这两种类型。

#### 意向锁

    意向锁属于表锁，实现对数据表的加锁。分为意向共享锁和意向互斥锁。
    意向锁之间不存在冲突的说法，他们互相兼容。
    那么意向锁到底有何用？
    主要是为了展示当前是否有其它事务在表中活跃，或者锁住了某行。
    好处就是直接通过意向锁就可以判断，不需要遍历所有行。

#### Record lock

    行锁，实现对记录中的索引加锁，如果没有索引，则对默认创建的索引加锁。
    锁住的始终是索引。

#### Gap Locks

    间隙锁，实现对某个区间(n,m)加锁。区间内存在的值和不存在的值都会加锁。
    间隙锁可以冲突，即同一个区间可以被不同事物加上间隙锁。比如A事物对区间(n,m)加了
    间隙共享锁，B事物仍然可以对区间(n,m)添加间隙互斥锁。

    比如一个索引，取值为10,11,13,20，需要对between 10 and 20加间隙锁，加锁后如下：
        (10,11)
        (11,13)
        (13,20)
    此时这个三个段都被加锁，不能有数据插入。假设，删除一条数据索引为11，那么间隙锁被合并，合并后如下：
        (10,13)
        (13,20)
    
    间隙锁主要为了防止区间内有插入数据出现。
    如果有记录被删除，那么该记录对应的锁会被合并。

#### Next-key lock
    
    是Gap lock和record lock的组合。

    假设索引包含10,11,12,13，那么这个索引对应的间隙锁如下：
        (负无穷, 10]
        (10, 11]
        (11, 13]
        (13, 20]
        (20, 正无穷)
    索引整个范围被分为以上几段，其中有边界关闭。

#### 插入意图锁

    事务在向间隙锁中插入数据时，必须要先拿到插入意图锁？

###### 思考

    SELECT * FROM child WHERE id = 100; 这个语句会怎么加锁？
    如果ID是unique index直接对索引加锁，否则对前面的间隙也加锁。

## 锁与事务的结合

    对于ACID中的标准中的I，即隔离性，MySQL的innodb存储引擎提供了四种隔离级别，分别是
    对未提交、读提交、可重复读、串行化。

#### 可重复读

    对于select ... for update / lock in share mode、update、delete操作，innoDB存储引擎
    会进行判断：
    如果使用唯一索引作为查询条件，则直接加record lock
    如果没有使用unique index，则加gap lock 或者 next-key lock。
    大体原理是这么实现。

#### Consistent NonLocking Reads

    通过不加锁的方式实现一致性读。
    一致性读会将数据库中某一时刻的snapshot呈现出来。
    
    在RC、RR隔离级别下，对于select语句的查询默认模式是一致性读。一致性读时，事务不会对操作的表设置
    任何锁，因此其它事务可以访问当前表，提高表的并发度。

    那么一致性读是怎么实现的呢？
    具体来说，当select读请求事务到达后，InnoDB引擎会以当前作为一个timepoint，形成一个快照。使得读事务
    只可以看到当前快照中的信息，如果另一个事务到达，并且删除了一行数据，在timepoint之前做了提交。当前事务
    让然不可见。

    举个例子：
        SELECT COUNT(c1) FROM t1 WHERE c1 = 'xyz'; // 开启一个一致性读，此时没有加任何锁
        -- Returns 0: no rows match. // 返回0
        DELETE FROM t1 WHERE c1 = 'xyz'; // 删除对应值,为什么会突然有值了呢，因此一致性读完后，其它事务插入了新数据
        -- Deletes several rows recently committed by other transaction.
        
        // 下面同理
        SELECT COUNT(c2) FROM t1 WHERE c2 = 'abc';
        -- Returns 0: no rows match.
        UPDATE t1 SET c2 = 'cba' WHERE c2 = 'abc';
        -- Affects 10 rows: another txn just committed 10 rows with 'abc' values.
        SELECT COUNT(c2) FROM t1 WHERE c2 = 'cba';
        -- Returns 10: this txn can now see the rows it just updated.

    再来个例子：
        
                         Session A              Session B // A 和 B 两个事务
    
               SET autocommit=0;            SET autocommit=0;
        time   // 此刻作为timepoint
        |      SELECT * FROM t;
        |          empty set // 为空
        |                                   INSERT INTO t VALUES (1, 2);
        |
        v      SELECT * FROM t; // B事务插入后，依旧为空
                empty set
                                            COMMIT;
    
               SELECT * FROM t; // 即使B事务提交修改，依旧为空
               empty set
    
               COMMIT; // A 事务提交，当前timepoint失效
    
               SELECT * FROM t; // 重新创建新的timepoint，形成一个快照，然后读取到B事务提交的数据
               ---------------------
               |    1    |    2    |
               ---------------------
神奇不，虽然平时感觉是这样，但并不知其所以然，知其所以然，才可以更好用它。

###### 思考

    那么这种timepoint方式形成快照方式，实现一致性读的方式是怎么实现的呢？
    记住，只有select读事务才会使用这种方式实现。

具体参考[MVCC](./MVCC.md)
    