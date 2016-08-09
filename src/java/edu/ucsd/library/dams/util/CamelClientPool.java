package edu.ucsd.library.dams.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.apache.camel.CamelContext;
import org.apache.camel.util.IOHelper;
import org.apache.log4j.Logger;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Create pool connections for camel endpoint
 * @author lsitu
 */
public class CamelClientPool {
    private static Logger log = Logger.getLogger(CamelClientPool.class);

    private final BlockingQueue<CamelContext> pool;
    private final List<AbstractApplicationContext> camelAbsContexts;
    private int createdObjects = 0;
    private int size;
    private String config;

    public CamelClientPool(String config, int size) {
        pool = new ArrayBlockingQueue<>(size);
        camelAbsContexts = new ArrayList<>();
        this.config = config;
        this.size = size;
    }
 
    public CamelContext acquire() throws Exception {
        if (pool.peek() == null) {
            synchronized (CamelClientPool.class) {
                if( createdObjects < size )
                    return createObject();
            }
        }
        return pool.take();
    }
 
    public void recycle(CamelContext resource) throws Exception {
        pool.add(resource);
    }

    protected CamelContext createObject() throws Exception {
        AbstractApplicationContext absContext = new ClassPathXmlApplicationContext(config);
        CamelContext camelContext = absContext.getBean("camel-client", CamelContext.class);
        if (camelContext == null) {
            if (absContext != null)
                absContext.close();
            throw new Exception("CamelContext initiation failed.");
        }

        createdObjects++;
        camelAbsContexts.add(absContext);
        log.info("Number of Camel context created in pool: " + createdObjects + "; Maximum pool size: " + size + ".");
        return camelContext;
    }

    public synchronized void close() {
       pool.clear();
       for (AbstractApplicationContext absContext : camelAbsContexts) {
            IOHelper.close(absContext);
        }
        camelAbsContexts.clear();
    }
}
