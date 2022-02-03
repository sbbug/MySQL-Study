

## MYSQL debug

    debug是阅读学习源码的有效手段。那么MySQL该如何debug呢？
    
    编辑器：VS Code 
    虚拟机:VMware workstation 16 player
    操作系统：CentOS 7 x86_64
    mysql:

#### Debug
    
    两种思路：
    第一种通过vscode实现虚拟机上MySQL数据库的远程Debug。
    第二种直接在虚拟机上安装VS code，本地Debug
    
    采取第二种方式
    Vscode安装后启动命令：/usr/share/code/code --user-data-dir

    MySQL源码下载
    git 加速镜像域名：https://github.com.cnpmjs.org 好用!!

    cmake .. -DCMAKE_INSTALL_PREFIX=/home/shw/mysql \
    -DDEFAULT_CHARSET=utf8 \
    -DDEFAULT_COLLATION=utf8_general_ci \
    -DENABLED_LOCAL_INFILE=ON \
    -DWITH_INNODB_MEMCACHED=ON \
    -DWITH_INNOBASE_STORAGE_ENGINE=1 \
    -DDOWNLOAD_BOOST=1 \
    -DWITH_BOOST=/work/mysql-server/mysql-server/boost/boost_1_72_0
    -DCMAKE_BUILD_TYPE=Debug    

    yum install boost boost-devel boost-doc
    
    ...
    遇水搭桥，遇山开路！
    ...

    每次cmake之前记得删除缓存，重新来过！

    du -ah --max-depth=1

    MySQL启动：/usr/local/mysql/support-files/mysql.server start
    MySQL停止：/usr/local/mysql/support-files/mysql.sock stop
    
![debug界面](./img/img_2.png)

###### mysql.sock
    
   [关于mysql.sokc](https://segmentfault.com/a/1190000016098820)


###### vscode

    启动路径：/usr/share/code/code  --user-data-dir
    
## 参考

[cmake3安装](https://www.cnblogs.com/fps2tao/p/9341795.html)  
[GCC升级1](https://blog.csdn.net/weixin_39658118/article/details/110223894)  
[GCC升级2](https://www.cnblogs.com/NanZhiHan/p/11010130.html)  
[yum-erros](https://wiki.centos.org/yum-errors)  
[文件系统如何挂载到根目录](https://blog.51cto.com/zhangxueliang/2967817)  
[Centos根目录下各个目录介绍](https://www.cnblogs.com/CMX_Shmily/p/12033207.html)  
[GCC镜像下载加速](https://mirrors.tuna.tsinghua.edu.cn/gnu/gcc/gcc-8.3.0/)  
[libstdc++.so.6.0 not found](https://itbilu.com/linux/management/NymXRUieg.html)
[mysql编译安装](https://developer.aliyun.com/article/727403)  
[vscode远程debug配置](https://code.visualstudio.com/docs/remote/ssh)



    

    