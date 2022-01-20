
## MVCC 

    multi version currency control 简称MVCC，即多版本并发控制，是MySQL中用来实现数据并发访问控制的一种机制。

#### MVCC 与传统加锁机制区别

    在数据库中，多个事务为了实现对互斥资源的访问，往往通过加锁机制实现。比如共享锁、互斥锁。
    对于共享锁，多个读请求均可拿到该锁，实现记录行的读操作，对于互斥锁，一次只可以一个事务拿到，其它事务会
    被阻塞，严重影响执行效率，尤其是在高并发场景下。
    
    MySQL后续版本为了提升并发度，引入了乐观锁，即MVCC。MVCC是一种基于多版本协议的乐观锁机制，它可以有效提升资源访问的
    并发度。MySQL中的事务隔离级别RR(可重复读）、RC（读已提交）均使用了MVCC机制。

#### MVCC实现方式

    MVCC底层具体实现依赖了ReadView类。ReadView是一个类结构，每当一个事务发起读写请求的时候，都会在那一刻创建一个snapshot，
    用来记录当前事务信息。
    
    ReadView中具体属性有如下：
    // 首先假设一种场景，一个事务请求要访问某一条记录，记录该事务为ID
    private:
    // 如果事务ID>this.value，那么该事务不可见读到当前记录的数据
    trx_id_t m_low_limit_id;
    
    // 如果事务ID<this.value,那么该事务可以看到当前数据
    trx_id_t m_up_limit_id;
    
    // 创建当前事务的ID
    trx_id_t m_creator_trx_id;
    
    //ReadView创建那一刻，活跃的事务集合，包括读/写，即所有的未提交的事务ID，这些事务ID对应记录数据均未
    //提交，属于脏数据，不可以让其它事务读到。
    ids_t m_ids;
    
    /** The view does not need to see the undo logs for transactions
    whose transaction number is strictly smaller (<) than this value:
    they can be removed in purge if not needed by other views */
    trx_id_t m_low_limit_no;

#### MVCC中ReadView执行流程

    [[nodiscard]] bool changes_visible(trx_id_t id,// 需要判断的事务ID
                                     const table_name_t &name // 数据表名称) const {
    ut_ad(id > 0);
    
    // 事务ID小于最小事务ID或者等于创建该事务的ID
    if (id < m_up_limit_id || id == m_creator_trx_id) {
      return (true);
    }

    check_trx_id_sanity(id, name); // check事务ID是否合法
    
    // 事务ID大约或等于最大事务ID，说明该事务ID是在ReadView生成之后分配的，即新增加的事务，且未提交
    if (id >= m_low_limit_id) { 
      return (false);

    } else if (m_ids.empty()) { // 没有未提交事务，最好了
      return (true);
    }

    const ids_t::value_type *p = m_ids.data();
    
    // 如果ID既不小于最小值，也不大于等于最大值，则可能位置之间，二分搜索判断，若位于之间，说明是未提交事务。
    return (!std::binary_search(p, p + m_ids.size(), id));
    }
   
#### MVCC使用场景分析