# Zookeeper-Distributed-Lock  
zk实现分布式锁

一 while
简述
1.所有客户端都使用create方法创建同一个名字的节点
2.创建成功的获取到锁，其余的客户端不断while循环重试
3.获取到锁的客户端执行完逻辑代码后delete节点，其余客户端继续争抢


使用Zookeeper同名节点只能创建一次的特性，来实现独占锁
如果创建成功则拿到锁，创建失败抛出NodeExists 异常：
while循环用来获取锁失败后不断重试
缺点
1.while循环浪费cpu
2.频繁进行create重试会对Zookeeper服务器造成压力

二 Watcher
Watcher概述
在zookeeper中，watcher机制是当服务端的节点信息发生了变化时通知所有监听了该节点的客户端。
其允许客户端向服务端注册一个watcher监听，当服务端的一些指定事件触发了这个watcher，就会向指定的客户端发送一个事件通知。

1.所有客户端都使用create方法创建同一个名字的节点
2.创建成功的获取到锁，失败的客户端对该节点设置一个watcher
3.获取到锁的客户端delete节点，其余客户端收到watcher的通知，此时再执行一次create方法
4.1-3进行循环

缺点
当有极多的客户端在watcher等待锁时，一旦持有锁的客户端释放，就会引起“惊群效应”，无数个客户端的请求压力直接打到zookeeper服务器上

三 FairLock
zookeeper实现公平锁依赖于zookeeper的顺序节点
EPHEMERAL：临时节点，当客户端与ZooKeeper集合断开连接时，临时节点会自动删除。
PERSISTENT：持久节点，即使在创建该节点的客户端断开连接后，持久节点仍然存在。
EPHEMERAL_SEQUENTIAL：临时顺序节点  
PERSISTENT_SEQUENTIAL：持久顺序节点  

1.首先创建顺序节点，然后获取当前目录下最小的节点，判断最小节点是不是当前节点，如果是那么获取锁成功，如果不是那么获取锁失败。
2.获取锁失败的节点获取当前节点上一个顺序节点，对此节点注册watcher监听，并使当前线程进入await状态。
3.当监听的节点unlock删除节点之后会捕获到delete事件，这说明前面的线程都执行完了，当前线程唤醒，打断await状态，获取锁。

根据输出结果，可以看出来这次的客户端获取锁是公平的，排着队一个一个来的，因为每个节点都有自己的一个数字id，根据数字id来监听比自己小的节点，这样释放锁只唤醒一个客户端，而不会产生惊群效应。
# 封装Zk分布式锁    
  CountDownLatch downLatch1 = new CountDownLatch(1);    
  ZooKeeper zk = new ZooKeeper("localhost:2181", 6000, new Watcher() {  
      @Override  
  public void process(WatchedEvent watchedEvent) {  
    if (watchedEvent.getState() == Event.KeeperState.SyncConnected) {  
       downLatch1.countDown();  
      }  
   }     
  });  
downLatch1.await();  
ZkDistributedLock test = new ZkDistributedLock(zk);  
 test.lock(); //上锁  
//业务代码  
test.unlock(); //释放锁  
