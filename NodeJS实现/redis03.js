// 作业三
const Redis = require("ioredis");
const Entity = require("./util/entity");
const ports = [9000, 9001, 9002, 9003, 9004, 9005];
const host = "localhost";
// create cluster
const cluster = new Redis.Cluster(ports.map(port => ({host, port})));
async function main () {
    const comment = new Entity(cluster, "comment", ["context", "time"]);
    await comment.add({context: "dengpengfei is me", time: new Date(Date.now()).getDate() + ""});
    let data = await comment.query();
    console.log(data);
}
main();