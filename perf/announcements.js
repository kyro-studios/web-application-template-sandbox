// お知らせの一覧の性能試験 (NFR-3、アーキテクチャ設計 7.2)。
// 1,000 件が登録された状態で、100 人が GET /api/announcements を 1 分間繰り返す。
// この条件は設計の確認のためのもので、要件ではない。データの準備と実行の手順は perf/README.md を参照。

import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'https://localhost:8443';

export const options = {
    vus: 100,
    duration: '1m',
    // 開発の環境の証明書は自己署名のため、検証を省く
    insecureSkipTLSVerify: true,
    thresholds: {
        http_req_duration: ['p(95)<1000'],
        http_req_failed: ['rate==0'],
        checks: ['rate==1'],
    },
};

export default function () {
    const res = http.get(`${BASE_URL}/api/announcements`);

    check(res, {
        'status is 200': (r) => r.status === 200,
        'announcements has 20 items': (r) => r.status === 200 && countAnnouncements(r) === 20,
    });
}

// 本文の JSON が壊れていても試験を止めず、件数を -1 として check を失敗させる
function countAnnouncements(res) {
    try {
        return res.json('announcements').length;
    } catch (e) {
        return -1;
    }
}
