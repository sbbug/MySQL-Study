

## 倒排索引

    ES底层使用倒排索引实现数据的组织和存储。




## DSL基础语法

#### 普通查询语法积累


        查询语法格式

        query 
                Term
                Terms
                match_all // 在所有字段中搜索
                Match   // 在单个字段中搜索
                multi_match // 在多个指定的字段中搜索



                GET /ad_hour_data_realtime_v4/doc/_search
                {
                  "query": {
                    "bool": {
                      "filter": [
                            {
                                "term": {
                                            "agent": "qj_2022"
                                  }
                            },
                           {
                                "range": {
                                      "date": {
                                              "gte": "2022-11-10T04",
                                                "lte": "2022-11-11T04"
                                       }
                                }
                           }
                      ]
                    }
                  }
                }

                select * from ad_hour_data_realtime_v4 where agent = ‘qj_2022’ and (date between "2022-11-10T04" and "2022-11-11T04”)

                GET /ad_hour_data_realtime_v4/doc/_search
                {
                  "query": {
                    "bool": {
                        "must": [
                            {"term": {"account": "25955794"}},
                            {"term": {"channel": "GDTM"}},
                            {"range": {"date": {"gte": "2022-11-10T04","lte": "2022-11-15T04"}}}
                       ]
                    }
                  }
                }

                Select * from ad_hour_data_realtime_v4 where account = ‘25955794’ and channel = ‘GDTM’ and (date between "2022-11-10T04" and "2022-11-11T04”)


                GET /ad_hour_data_realtime_v4/doc/_search
                {
                  "query": {
                    "term":{
                      "adName.keyword":"1116-亿玛-29元-dyh-B1通投-大图-dt48CNN-通-下单-875-商品"
                    }
                  }
                }

                ES查询时不做分词

                GET /ad_hour_data_realtime_v4/doc/_search
                {
                  "query": {
                    "terms":{
                      "adName.keyword":["1116-亿玛-29元-dyh-B1通投-大图-dt48CNN-通-下单-875-商品",""]
                    }
                  }
                }

                Select * ad_hour_data_realtime_v4 where adName in (‘1116-亿玛-29元-dyh-B1通投-大图-dt48CNN-通-下单-875-商品’)



                GET /ad_hour_data_realtime_v4/doc/_search
                {
                  "query": {
                    "match":{
                      "channel":"GDT"
                    }
                  }  
                }

                // 文档字段channel包含’GDT’的都会被搜索出来

                GET /ad_hour_data_realtime_v4/doc/_search
                {

                    "from":0,
                    "size":1

                }


                match match_phrase mutil_match term 的区别

                {
                “Query”:{
                      “match”:{
                            “content”:”我的宝马多少马力”
                       }
                  }
                }

                // 查询会将内容分词为:宝马 多少 马力，凡是content字段包含任意一个或多个的都会被查出来。

                {
                “Query”:{
                      “match_phrase”:{
                            “content”:”我的宝马多少马力”
                       }
                  }
                }

                // 使用match_phrase可以实现完全匹配，匹配严格，content字段必须同时包含:宝马 多少 马力

                {
                “Query”:{
                    “mutil_match”:{
                        “Query”:”我得宝马多少马力”,
                         Fields:{“title”,”content”}
                  }
                }
                }
                // 在多个字段中进行匹配

                Term是进行完全匹配，不进行分词分析，文档必须包含整个搜索的词汇。

## 参考资料

[1. 官方文档](https://www.elastic.co/cn/webinars/getting-started-elasticsearch)
[2. ES实战]()
