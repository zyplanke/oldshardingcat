
package io.shardingcat.statistic;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * SQL统计排序记录器
 *
 * @author shardingcat
 */
public final class SQLRecorder {

    private final int count;
    SortedSet<SQLRecord> records;

    public SQLRecorder(int count) {
        this.count = count;
        this.records = new ConcurrentSkipListSet<>();
    }

    public List<SQLRecord> getRecords() {
        List<SQLRecord> keyList = new ArrayList<SQLRecord>(records);
        return keyList;
    }


    public void add(SQLRecord record) {
        records.add(record);
    }

    public void clear() {
        records.clear();
    }

    public void recycle(){
        if(records.size() > count){
            SortedSet<SQLRecord> records2 = new ConcurrentSkipListSet<>();
            List<SQLRecord> keyList = new ArrayList<SQLRecord>(records);
            int i = 0;
            for(SQLRecord key : keyList){
                if(i == count) {
                    break;
                }
                records2.add(key);
                i++;
            }
            records = records2;
        }
    }
}