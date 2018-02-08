package com.fenlan.storm.storm;

import com.fenlan.storm.Properties.RedisProperties;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpliteBolt extends BaseRichBolt {

    private OutputCollector collector;
    private static String redisHost = RedisProperties.getRedisHost();
    private static int redisPort = RedisProperties.getredisPort();
    private static Jedis jedis = new Jedis(redisHost, redisPort);
    private Map<String, Integer> day_counter;
    private static Integer day;

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.collector = outputCollector;
        this.day_counter = new HashMap<>();
    }

    @Override
    public void execute(Tuple tuple) {
        long index = tuple.getLong(0);
        String record = tuple.getString(1);

        String regx = "([^ ]*) ([^ ]*) ([^ ]*) (\\[.*\\]) (\\\".*?\\\") (-|[0-9]*) (-|[0-9]*) (\\\".*?\\\") (\\\".*?\\\")";
        Pattern pattern = Pattern.compile(regx);
        Matcher matcher = pattern.matcher(record);

        if (matcher.find()) {
            String remote_addr = matcher.group(1);
            String time_local = matcher.group(4).substring(1, matcher.group(4).length()-1);
            String request = matcher.group(5);
            String status = matcher.group(6);
            String body_bytes_sent = matcher.group(7);
            String virtual_host = matcher.group(8).substring(1, matcher.group(8).length()-1);
            String http_user_agent = matcher.group(9);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);
            LocalDateTime dateTime = LocalDateTime.parse(time_local, formatter);
            Long milli_time = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            addToDay(dateTime);
            jedis.hset("days_counter", day.toString(), day_counter.get(day.toString()).toString());

//            pipelineq.sadd("remote_addr", milli_time + "##" + remote_addr);
//            pipelineq.sadd("request", milli_time + "##" + request);
//            pipelineq.sadd("status", milli_time + "##" + status);
//            pipelineq.sadd("body_bytes_sent", milli_time + "##" + body_bytes_sent);
//            pipelineq.sadd("virtual_host", milli_time + "##" + virtual_host);
//            pipelineq.sadd("http_user_agent", milli_time + "##" + http_user_agent);

            collector.emit(new Values("remote_addr", milli_time + "##" + remote_addr));
            collector.emit(new Values("request", milli_time + "##" + request));
            collector.emit(new Values("status", milli_time + "##" + status));
            collector.emit(new Values("body_bytes_sent", milli_time + "##" + body_bytes_sent));
            collector.emit(new Values("virtual_host", milli_time + "##" + virtual_host));
            collector.emit(new Values("http_user_agent", milli_time + "##" + http_user_agent));
        }
        else {
            System.out.println("NO MATCH");
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declare(new Fields("item", "value"));
    }

    // 每天浏览量统计
    private void addToDay(LocalDateTime localDateTime) {
        day = localDateTime.getYear() * 10000 +
                localDateTime.getMonthValue() * 100 + localDateTime.getDayOfMonth();
        CounterBolt.counter(day_counter, day.toString());
    }
}
