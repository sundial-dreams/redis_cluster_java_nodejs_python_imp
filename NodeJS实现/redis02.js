// 作业二
const Redis = require("ioredis");
const { ConsistentHash } = require("./util/utils");
const Entity = require("./util/entity");
const host = "localhost";
const ports = [8000, 8001, 8002];
// 创建代理对象，对ioredis包的方法进行代理，以实现客户端分片
function createProxy(ports) {
    function _redis(key = "") {
        const consistenhash = new ConsistentHash(ports, 100);
        const redisMapping = new Map( ports.map(port => [port, new Redis({host, port})]) );
        return redisMapping.get(+ consistenhash.visit(key));
    }
    const redis = {};
    for (let k in Redis.prototype) {
        redis[k] = (...args) => _redis(args[0])[k](...args);
    }
    return redis
}
const proxyRedis = createProxy(ports);

async function main () {
    const user = new Entity(proxyRedis, "user", ["name", "age", "sex"]);
    await user.add({name: "dpf", age: 21, sex: "man"});
    let data = await user.query();
    console.log(data);
}
main();