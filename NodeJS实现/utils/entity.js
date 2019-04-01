const { object2array } = require("./utils");
// 定义跟第一次作业一样的实体类
module.exports = class Entity {
    constructor (redis,key, attr) {
        Object.assign(this, { key, attr, redis });
    }
    
    setDriver(redis) {
        this.redis = redis;
    }

    async add (obj) {
        const { key, attr, redis } = this;
        if (!attr.every(k => obj[k])) throw new Error("error");
        const id = await redis.incr(key + ":");
        await Promise.all([redis.sadd(`${key}:index`, `${key}:${id}`), redis.hmset(`${key}:${id}`, object2array(obj))]);
    }
    
    async query (id = false) {
        const { key, redis } = this;
        if (id && await redis.exists(`${key}:${id}`)) return redis.hgetall(`${key}:${id}`);
        else {
            let all = await redis.smembers(`${key}:index`);
            all = all.map(async v =>  await redis.hgetall(v));
            return await Promise.all(all);
        } 
    }

    async deleteOne (id) {
        const { key, redis } = this;
        if (id && await redis.exists(`${key}:${id}`)) {
            await Promise.all([redis.del(`${key}:${id}`), redis.srem(`${key}:index`, `${key}:${id}`)]);
        }
    }

    async update (id, newAttr) {
        const { key, redis } = this;
        if (id && await redis.exists(`${key}:${id}`)) {
            let ps = [];
            for (let k of newAttr) {
                ps.push(redis.hset(`${key}:${id}`, k, newAttr[k]));
            }
            await Promise.all(ps);
        }
    }
}