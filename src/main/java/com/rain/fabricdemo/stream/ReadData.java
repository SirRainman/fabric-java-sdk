package com.rain.fabricdemo.stream;

import com.rain.fabricdemo.dto.DataItem;
import com.rain.fabricdemo.handler.DataHandler;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ReadData extends TimerTask {
    private int maxDataSize = 1;
    private static final Object lock = new Object();
    private static final ExecutorService executorService = Executors.newFixedThreadPool(80);
    private Timer timer;
    private static int generateOnce = 100000;
    private static final Long period = 100L; // 单位是ms    每隔一段时间产生一定量的数据
    private final Set<DataItem> DataPool = new HashSet<>();

    public ReadData() {
        Timer timer = new Timer();
        // 1模拟生产者和消费者
        //timer.scheduleAtFixedRate(this, 0, period);
        // 2一次性生产出来数据
        timer.schedule(this, 3);
        this.timer = timer;
    }

    public long read(final DataHandler handler) {
        int hasReadItemCount = 0;
        boolean isLimit = maxDataSize > 0;

        List<DataItem> needToRemoveList = new ArrayList<>();
        while (true) {
            synchronized (lock) {
                if (DataPool.size() <= 0) {
                    continue;
                }
                needToRemoveList.clear();
                for (final DataItem dataItem : DataPool) {
                    executorService.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                handler.handle(dataItem);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });

                    needToRemoveList.add(dataItem);

                    if (isLimit == true) {
                        hasReadItemCount++;
                    }
                }
                DataPool.removeAll(needToRemoveList);
            }
            if (isLimit == true && hasReadItemCount >= maxDataSize) {
                break;
            }
        }
        executorService.shutdown();

        try {
            while (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                System.out.println("1 second passed...");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("read over");
        return System.currentTimeMillis();
    }

    public void preProduceSomeRequest() {
        List<DataItem> dataItems = new ArrayList<>();
        for (int i = 0; i < generateOnce; i++) {
            // 随机生成查询请求
            DataItem dataItem = new DataItem();
            // dataItem.operation = "query";
            dataItem.operation = "Init";
            int account = new Random().nextInt(100);
            dataItem.from = (account < 50 ? "a" : "b");
            dataItems.add(dataItem);
        }

        synchronized (lock) {
            DataPool.addAll(dataItems);
        }
    }

    // 模拟生产者和消费者模型，每隔一段时间生产一些数据
    @Override
    public void run() {
        List<DataItem> dataItems = new ArrayList<>();
        for (int i = 0; i < generateOnce; i++) {
            // 随机生成查询请求
            DataItem dataItem = new DataItem();
            // dataItem.operation = "query";
            dataItem.operation = "Init";
            int account = new Random().nextInt(100);
            dataItem.from = (account < 50 ? "a" : "b");
            dataItems.add(dataItem);
        }

        synchronized (lock) {
            DataPool.addAll(dataItems);
        }
    }

    public void stop() {
        if (this.timer != null) {
            this.timer.cancel();
        }
    }
}
