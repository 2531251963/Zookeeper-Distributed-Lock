package org.easyarch.zk_distributedLock;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * @ClassName ZkDistributedLock
 * @Description TODO
 * @Author Liyihe
 * @Date 2019/09/01 下午5:48
 * @Version 1.0
 */
public class ZkDistributedLock {
    private ZooKeeper zk;
    final String mainNodename = "/Zk_DistributedLock";
    String lockNodename;

    public ZkDistributedLock(ZooKeeper zk) {
        this.zk = zk;
        try {
            zkisConnection();
            if (zk.exists(mainNodename, null) == null) {
                zk.create(mainNodename, "mainNode".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void lock() {
        try {
            String path = zk.create(mainNodename + "/lock_", "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            lockNodename = path;
            List<String> minPath = zk.getChildren(mainNodename, false);
            Collections.sort(minPath);
            String min = minPath.get(0);
            if (path != null && !path.isEmpty() && min != null && !min.isEmpty() && path.equals(mainNodename + "/" + min)) {
                return;
            }
            String watchNode = null;
            if (path != null) {
                for (int i = minPath.size() - 1; i >= 0; i--) {
                    if (minPath.get(i).compareTo(path.substring(path.lastIndexOf("/") + 1)) < 0) {
                        watchNode = minPath.get(i);
                        break;
                    }
                }
            }
            CountDownLatch latch = new CountDownLatch(1);
            if (watchNode != null) {
                Stat stat = zk.exists(mainNodename + "/" + watchNode, watchedEvent -> {
                    if (watchedEvent.getType() == Watcher.Event.EventType.NodeDeleted) {
                        latch.countDown();
                    }
                });
                if (stat != null) {
                    System.out.println(Thread.currentThread().getName() + " waiting for " + mainNodename + "/" + watchNode);
                }
            }
            latch.await();
            System.out.println(Thread.currentThread().getName() + "  获取锁...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void unlock() {
        try {
            System.out.println(Thread.currentThread().getName() + "释放 Lock...");
            zk.delete(lockNodename, -1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void zkisConnection() throws Exception {
        if (zk == null || !zk.getState().isConnected()) {
            throw new Exception("zk is not Connection");
        }
    }
}
