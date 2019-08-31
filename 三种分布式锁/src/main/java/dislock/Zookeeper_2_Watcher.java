package dislock;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

/**
 * @ClassName Zookeeper_2_Watcher
 * @Description TODO
 * @Author Liyihe
 * @Date 19-8-7 下午7:41
 * @Version 1.0
 */


/**
 * Zookeepr实现分布式锁
 */
public class Zookeeper_2_Watcher {
    private String zkconfig = "localhost:2181";

    private static String lockNameSpace = "/mylock";

    private String nodeString = lockNameSpace + "/lock1";

    private static ZooKeeper zk;

    public Zookeeper_2_Watcher() {
        try {
            zk = new ZooKeeper(zkconfig, 6000, new Watcher() {
                @Override
                public void process(WatchedEvent watchedEvent) {
                    System.out.println("Receive event " + watchedEvent);
                    if (Event.KeeperState.SyncConnected == watchedEvent.getState()) {
                        System.out.println("connection is ok");
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void watchNode(String nodeString, final Thread thread) throws InterruptedException {
        try {
            zk.exists(nodeString, new Watcher() {
                @Override
                public void process(WatchedEvent watchedEvent) {
                    System.out.println("==" + watchedEvent.toString());
                    if (watchedEvent.getType() == Event.EventType.NodeDeleted) {
                        System.out.println("Threre is a Thread released Lock==============");
                        thread.interrupt();
                    }
                }
            });
        } catch (KeeperException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取锁
     *
     * @return
     * @throws InterruptedException
     */
    public boolean lock() throws Exception {
        String path = null;
        while (true) {
            CountDownLatch countDownLatch =  new CountDownLatch(1);
            try {
                path = zk.create(nodeString, "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
            } catch (KeeperException e) {
                System.out.println(Thread.currentThread().getName() + "  请求失败");

                try {
                    zk.exists(nodeString, new Watcher() {
                        @Override
                        public void process(WatchedEvent watchedEvent) {
                            System.out.println("事件来了：" + watchedEvent.toString());
                            if (watchedEvent.getType() == Event.EventType.NodeDeleted) {
                                System.out.println("delete事件");
                                countDownLatch.countDown();
                            }
                        }
                    });
                } catch (KeeperException e1) {
                    e.printStackTrace();
                }
            }
            if (path != null && !path.isEmpty()) {
                System.out.println(Thread.currentThread().getName() + " 拿到 Lock...");
                break;
            }
            countDownLatch.await();
        }
        return true;

    }



    /**
     * 释放锁
     */
    public void unlock() {
        try {
            zk.delete(nodeString, -1);
            System.out.println(Thread.currentThread().getName() +  "释放 Lock...");
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        }
    }

    public static void main(String args[]) throws InterruptedException {
        Zookeeper_2_Watcher test = new Zookeeper_2_Watcher();
        try {
            Thread.sleep(100);
            zk.create(lockNameSpace, "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        } catch (KeeperException e) {
        } catch (InterruptedException e) {
        }
        ExecutorService service = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 4; i++) {
            service.execute(() -> {
                try {
                    test.lock();
                    Thread.sleep(1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                test.unlock();
            });
        }
        service.shutdown();
    }
}