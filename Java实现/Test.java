import io.lettuce.core.ReadFrom;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import io.lettuce.core.codec.Utf8StringCodec;
import io.lettuce.core.masterslave.MasterSlave;
import io.lettuce.core.masterslave.StatefulRedisMasterSlaveConnection;

import java.util.Arrays;
import java.util.List;

class Comment {
    private String text;
    public String article;

    Comment(String article, String text) {
        this.article = article;
        this.text = text;
    }
}

public class Test {
    static private final String host = "localhost";

    static public void main(String[] args) throws Exception {
        sentinelMode();
        clusterMode();
        clientSelectionMode();
    }

    //一主三从三哨兵模式
    static public void sentinelMode() throws Exception {
        RedisClient redisClient = RedisClient.create();
        RedisURI rUri = RedisURI.Builder
                .sentinel(host, 26379, "mymaster")
                .withSentinel(host, 26380)
                .withSentinel(host, 26381).build();
        // 连接哨兵
        StatefulRedisMasterSlaveConnection<String, String> connection = MasterSlave.connect(
                redisClient, new Utf8StringCodec(), rUri
        );
        // 从从节点里读
        connection.setReadFrom(ReadFrom.SLAVE_PREFERRED);
        RedisAsyncCommands<String, String> asyncCommands = connection.async();
        Dao<Comment> comment = new Dao<>(Comment.class).withAsync(asyncCommands);
        comment.insert(new Comment("JavaScript async", "it is so good"));
        System.out.println(comment.queryAll());
    }
    // 客户端分片模式
    static public void clientSelectionMode() throws Exception {
        List<RedisURI> nodes = Arrays.asList(
                RedisURI.create(host, 8000),
                RedisURI.create(host, 8001),
                RedisURI.create(host, 8002)
        );
        RedisClusterClient clusterClient = RedisClusterClient.create(nodes);
        StatefulRedisClusterConnection<String, String> connection = clusterClient.connect();
        RedisClusterAsyncCommands<String, String> asyncCommands = connection.async();
        Dao<Comment> comment = new Dao<>(Comment.class).withAsync(asyncCommands);
        comment.insert(new Comment("Java", "Java is a good language"));
        System.out.println(comment.queryAll());
    }

    // 六节点集群模式
    static public void clusterMode() throws Exception {
        RedisClient redisClient = RedisClient.create();
        List<RedisURI> nodes = Arrays.asList(
                RedisURI.create(host, 9000),
                RedisURI.create(host, 9001),
                RedisURI.create(host, 9002),
                RedisURI.create(host, 9003),
                RedisURI.create(host, 9004),
                RedisURI.create(host, 9005)
        );
        // 连接集群节点
        StatefulRedisMasterSlaveConnection<String, String> connection = MasterSlave.connect(
                redisClient, new Utf8StringCodec(), nodes
        );
        connection.setReadFrom(ReadFrom.MASTER_PREFERRED);
        RedisAsyncCommands<String, String> asyncCommands = connection.async();
        Dao<Comment> comment = new Dao<>(Comment.class).withAsync(asyncCommands);
        comment.insert(new Comment("Javascript", "Javascript is good language"));
        System.out.println(comment.queryAll());
    }
}