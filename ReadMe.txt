第二作业
软件16-3班 邓鹏飞 1614010303

使用Java, Nodejs, Python分别尝试去实现主从哨兵节点的连接，客户端分片，集群连接

Java：
JDK 1.8，redis 5.0.3, Lettuce 5.1.3
使用Lettuce包，自带哨兵节点连接，客户端分片，以及集群连接，复用第一次作业的Dao类，实现增删改查

Nodejs：
nodejs 11.13.0, ioredis 4.9.0, redis 5.0.3
使用ioredis包，自带哨兵节点连接，集群连接，没有客户端分片，所以自己实现了一致性hash算法(位于utils/utils.js)，并使用代理模式，代理ioredis里面自带的方法
实现了一个类似于Java中的Dao的Entity类来做增删改查

Python：
python 3.6, aioredis 1.0, redis 5.0.3
使用aioredis包，自带哨兵节点连接，没有集群连接，客户端分片，所以只实现了哨兵节点的连接
实现了一个类似于Java中的Dao的Entity类来做增删改查