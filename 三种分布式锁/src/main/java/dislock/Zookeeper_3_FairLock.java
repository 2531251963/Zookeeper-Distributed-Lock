package dislock;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/**
 * @ClassName Zookeeper_3_FairLock
 * @Description TODO
 * @Author Liyihe
 * @Date 19-8-7 下午7:47
 * @Version 1.0
 */

public class Zookeeper_3_FairLock {

    private String zkconfig = "localhost:2181";

    private String lockName = "/mylock";

    private String lockZnode = null;

    private ZooKeeper zk;

    private CountDownLatch countDownLatch=new CountDownLatch(1);

    public Zookeeper_3_FairLock(){
        try {
            zk = new ZooKeeper(zkconfig, 6000, new Watcher() {
                @Override
                public void process(WatchedEvent watchedEvent) {
                    System.out.println("Receive event "+watchedEvent);
                    if(Event.KeeperState.SyncConnected == watchedEvent.getState())
                        System.out.println("connection is ok");
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    /**
     * 获取锁
     * @return
     * @throws InterruptedException
     */
    public void lock(){
        String path = null;
        try {
            path = zk.create(lockName+"/mylock_", "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            lockZnode = path;
            List<String> minPath = zk.getChildren(lockName,false);
            System.out.println(minPath);
            Collections.sort(minPath);
            System.out.println("最小的节点是："+minPath.get(0));
            if (path!=null&&!path.isEmpty()
                    &&minPath.get(0)!=null&&!minPath.get(0).isEmpty()
                    &&path.equals(lockName+"/"+minPath.get(0))) {
                System.out.println(Thread.currentThread().getName() + "  获取锁...");
                return;
            }
            String watchNode = null;
            if (path!=null) {
                for (int i = minPath.size() - 1; i >= 0; i--) {
                    if (minPath.get(i).compareTo(path.substring(path.lastIndexOf("/") + 1)) < 0) {
                        watchNode = minPath.get(i);
                        break;
                    }
                }
            }
            System.out.println(watchNode);
            if (watchNode!=null){
                Stat stat = zk.exists(lockName + "/" + watchNode,new Watcher() {
                    @Override
                    public void process(WatchedEvent watchedEvent) {
                        if(watchedEvent.getType() == Event.EventType.NodeDeleted){
                            System.out.println("delete事件来了");
                            countDownLatch.countDown();
                            System.out.println("打断当前线程");
                        }
                    }
                });
                if(stat != null){
                    System.out.println(Thread.currentThread().getName() + " waiting for " + lockName + "/" + watchNode);
                }
            }
            countDownLatch.await();
            System.out.println(Thread.currentThread().getName() + " 被唤醒");
            System.out.println(Thread.currentThread().getName() + "  获取锁...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 释放锁
     */
    public void unlock(){
        try {
            System.out.println(Thread.currentThread().getName() +  "释放 Lock...");
            zk.delete(lockZnode,-1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        }
    }



    public static void main(String args[]) throws InterruptedException {
        ExecutorService service = Executors.newFixedThreadPool(10);
        for (int i = 0;i<3;i++){
            service.execute(()-> {
                Zookeeper_3_FairLock test = new Zookeeper_3_FairLock();
                try {
                    test.lock();
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                test.unlock();
            });
        }
        service.shutdown();
    }

}