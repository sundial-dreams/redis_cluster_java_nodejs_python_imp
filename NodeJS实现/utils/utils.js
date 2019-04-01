// 对象转数组 例如{a:1, b:2} => [a,1,b,2]
exports.object2array = function (object = {}) {
    if (typeof object !== "object") throw new Error("arguments error");
    let array = [];
    for (let key in object) {
        array.push(key, object[key]);
    }
    return array;
}

exports.object2array_max = (object = {}) => Object.keys(object).reduce((acc, key) => (acc.push(key, object[key], acc)), []);

// 实现一致性哈希算法，进行客户端分片
exports.ConsistentHash = class {
    constructor (nodes, virtualNodesPerRealNode) {
        this.VN_RN = virtualNodesPerRealNode; 
        this.realNodes = [];
        this.mapping = new Map();
        this.virtualNodes = new Map();
        this.VNodeNumber = 0;
        this.addNode(...nodes);
    }
    getHash(str = "") {
        const p = 16777619;
        let hash = 2166136261;
        for (let i = 0, len = str.length; i < len; i++) {
            hash = (hash ^ str.charAt(i)) * p;
        }
        hash += hash << 13;
        hash ^= hash >> 7;
        hash += hash << 3;
        
        hash ^= hash >> 17;
        hash += hash << 5;
        return hash < 0 ? Math.abs(hash) : hash;
    }
    addNode(...nodes) {
        for (let node of nodes) {
            this.realNodes.push(node);
            const list = [];
            for (let count = 0, seq = 0; count < this.VN_RN;) {
                let VNodeName = node + "##VN" + (seq ++);
                let hash = this.getHash(VNodeName);
                if (!this.virtualNodes.has(hash)) {
                    this.virtualNodes.set(hash, VNodeName);
                    count ++;
                    list.push(VNodeName);
                }
            }
            this.mapping.set(node, list);
        }
        this.VNodeNumber = this.realNodes.length * this.VN_RN;
    }

    removeNode (...nodes) {
        for (let node of nodes) {
            let index = this.realNodes.indexOf(node);
            if (index !== -1) {
                this.realNodes.splice(index, 1);
            }
            if (this.mapping.has(node)) {
                let list = this.mapping.get(node);
                this.mapping.delete(node);
                for (let v of list) {
                    this.virtualNodes.delete(this.getHash(v))
                }
            }
        }
        this.VNodeNumber = this.realNodes.length * this.VN_RN;
    }

    visit (key = "") {
        let hash = this.getHash(key);
        let newMap = new Map( [...this.virtualNodes].filter(([k]) => (k > hash)).sort(([k], [k1]) =>  k > k1 ? 1 : -1) );
        let vNode = null;
        if (!newMap.size) {
            let [k, v] = [...this.virtualNodes][0];
            vNode = v;
        } else {
            let [k, v] = [...newMap][0];
            vNode = v;
        }
        if (vNode) {
            vNode = vNode.split("##")[0];
        }
        return vNode
    }

}