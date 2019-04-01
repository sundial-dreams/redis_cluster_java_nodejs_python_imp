import io.lettuce.core.LettuceFutures;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


/**
 * Dao 接口
 *
 * @param <E>
 */
interface IDao<E> {
    //查询所有 返回一个Map类型的值，其中键为Id，值为泛型类对象(实体类对象，如User类的对象)
    Map<String, E> queryAll() throws Exception;

    //基于域查询 返回值与queryAll一致， 根据对应属性的值来查询，以User类为例，queryByField("name", "邓鹏飞") => 查询叫做邓鹏飞的用户ID
    Map<String, E> queryByField(String field, String value) throws Exception;

    //基于Id查询
    Tuple2<String, E> queryById(String id) throws Exception;

    //插入操作
    void insert(E ele) throws Exception;

    //更新操作
    void update(String id, E ele) throws Exception;

    //删除操作
    void delete(String id) throws Exception;
}

/**
 * Dao类
 * JDK 1.8
 * 使用lettuce 5.0.3 且使用异步命令
 * 数据库存储结构
 * 以User为例
 * User: 类型String 记录user id
 * User:id 类型Hash 记录User信息
 * User:fields:attr:value 类型SET 反向索引 属性与键
 * User:index 记录所有user 的key 类型ZSET
 *
 * @param <T>
 */
public class Dao<T> implements IDao<T> {
    private Class<T> _class;
    protected String dbName;
    protected final String SEP = ":";
    private final String NS_FIELDS = "fields";
    private final String NS_INDEX = "index";
    // 使用lettuce异步命令
    private RedisClient redisClient = RedisClient.create();
    protected RedisClusterAsyncCommands<String, String> async = redisClient.connect().async();

    public Dao<T> withAsync(RedisClusterAsyncCommands<String, String> async) {
        this.async = async;
        return this;
    }

    public Dao(Class<T> oClass) {
        _class = oClass;
        dbName = _class.getName().toLowerCase();
    }

    private String makePrimaryKey(String id) {
        return (dbName + SEP + id).trim();
    }

    private String makeIndexKey() {
        return (dbName + SEP + NS_INDEX).trim();
    }

    private String makeSetKey(String field, String value) {
        return (dbName + SEP + NS_FIELDS + SEP + field + SEP + value).trim();
    }


    /**
     * @return Map ，{id, {...}}
     * @throws Exception
     */
    public Map<String, T> queryAll() throws Exception {
        String indexKey = makeIndexKey();
        if (async.exists(indexKey).get(1, TimeUnit.MINUTES) == null) throw new Exception("key is not exist");
        return async.zrange(indexKey, 0, -1).get(1, TimeUnit.MINUTES)// user:1 user:2 ........
                .parallelStream()   // 使用Jdk1.8的并行流
                .map(key -> {
                    String[] k = key.split(SEP);
                    try {
                        return queryById(k[1]);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Tuple2::getT1, Tuple2::getT2));
    }

    /**
     * 基于Field查询，即queryByField("name", "邓鹏飞")
     *
     * @param field
     * @param value
     * @return Map, {id, {...}}
     * @throws Exception
     */
    public Map<String, T> queryByField(String field, String value) throws Exception {
        if (_class.getField(field) == null) throw new Exception("field is not exist");
        String sKey = makeSetKey(field, value);
        if (async.exists(sKey).get(1, TimeUnit.MINUTES) == null) throw new Exception("key is not exist");
        return async.smembers(sKey).get(1, TimeUnit.MINUTES) // user:1 ,user:2 ....
                .parallelStream()
                .map(key -> {
                    String id = key.split(SEP)[1];
                    try {
                        return queryById(id);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Tuple2::getT1, Tuple2::getT2));
    }

    /**
     * Id查询
     *
     * @param id
     * @return Tuple2, 即(id, {...})
     * @throws Exception
     */
    public Tuple2<String, T> queryById(String id) throws Exception {
        String queryKey = makePrimaryKey(id);
        if (async.exists(queryKey).get(1, TimeUnit.MINUTES) == null) throw new Exception("key is not exist");
        Map<String, String> map = async.hgetall(queryKey).get(1, TimeUnit.MINUTES);
        T object = _class.getDeclaredConstructor().newInstance();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String k = entry.getKey(), v = entry.getValue();
            Field field = _class.getDeclaredField(k);
            if (!field.isAccessible()) field.setAccessible(true);
            field.set(object, v);
        }
        return new Tuple2<>(id, object);
    }

    /**
     * 插入
     * 这里利用反射，对实体类的公有属性设置反向索引
     *
     * @param ele
     * @throws Exception
     */
    public void insert(T ele) throws Exception {
        Field[] publicFields = _class.getFields(),
                fields = _class.getDeclaredFields();
        Map<String, String> map = new TreeMap<>();
        List<RedisFuture> futures = new LinkedList<>();
        Long id = async.incr(dbName + SEP).get(1, TimeUnit.MINUTES);
        String queryKey = makePrimaryKey(id + "");
        String indexKey = makeIndexKey();
        for (Field field : publicFields) {
            String fieldName = field.getName(),
                    value = (String) field.get(ele),
                    sKey = makeSetKey(fieldName, value);
            futures.add(async.sadd(sKey, queryKey));
        }
        for (Field field : fields) {
            if (!field.isAccessible()) field.setAccessible(true);
            String fieldName = field.getName(),
                    value = (String) field.get(ele);
            map.put(fieldName, value);
        }
        futures.add(async.hmset(queryKey, map));
        futures.add(async.zadd(indexKey, id, queryKey));
        // 等待所有Future对象完成
        LettuceFutures.awaitAll(Duration.ofSeconds(5), futures.toArray(new RedisFuture[0]));
    }

    /**
     * 更新
     * 主要维护两种键
     * 以User为例
     * User:1 => 类型Hash，存储实体类User的属性，也是更新操作的主要目标
     * User:fields:name:邓鹏飞 => 类型Set，存储姓名的反向索引，用于快速查询对应名字的键
     * 以更新名字为例，我们不仅需要改Hash类型的名字，而且还需要对应名字的更新反向索引
     *
     * @param id
     * @param obj
     * @throws Exception
     */
    public void update(String id, T obj) throws Exception {
        String queryKey = makePrimaryKey(id);
        if (async.exists(queryKey).get(1, TimeUnit.MINUTES) == null) throw new Exception("error");
        T object = queryById(id).getT2();
        Field[] fields = _class.getDeclaredFields(),
                publicFields = _class.getFields();
        for (Field field : fields) if (!field.isAccessible()) field.setAccessible(true);
        List<RedisFuture> futures = new LinkedList<>();
        for (Field field : publicFields) {
            if (field.get(obj) != null && !(field.get(obj)).equals("")) {
                String oldKey = makeSetKey(field.getName(), (String) field.get(object)),
                        newKey = makeSetKey(field.getName(), (String) field.get(obj));
                if (!field.get(object).equals(field.get(obj))) {
                    futures.add(async.srem(oldKey, queryKey));
                    futures.add(async.sadd(newKey, queryKey));
                }
            }
        }
        for (Field field : fields)
            if (field.get(obj) != null && !(field.get(obj).equals("")))
                futures.add(async.hset(queryKey, field.getName(), (String) field.get(obj)));

        for (Field field : publicFields) {
            if (field.get(obj) != null && !(field.get(obj).equals(""))) {
                String sKey = makeSetKey(field.getName(), (String) field.get(obj));
                futures.add(async.sadd(sKey, queryKey));
            }
        }
        // 等待所有Future对象完成
        LettuceFutures.awaitAll(Duration.ofSeconds(5), futures.toArray(new RedisFuture[0]));
    }

    /**
     * 删除对应的键
     *
     * @param id
     * @throws Exception
     */
    public void delete(String id) throws Exception {
        String queryKey = makePrimaryKey(id);
        if (async.exists(queryKey).get(1, TimeUnit.MINUTES) == null) throw new Exception("has error");
        T object = queryById(id).getT2();
        List<RedisFuture> futures = new LinkedList<>();
        futures.add(async.del(queryKey));
        futures.add(async.zrem(makeIndexKey(), queryKey));
        Field[] publicFields = _class.getFields();
        for (Field field : publicFields) {
            String sKey = makeSetKey(field.getName(), (String) field.get(object));
            futures.add(async.srem(sKey, queryKey));
        }
        // 等待所有Future对象完成
        LettuceFutures.awaitAll(Duration.ofSeconds(5), futures.toArray(new RedisFuture[0]));
    }
}
