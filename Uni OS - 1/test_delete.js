const axios = require('axios');

async function testDelete() {
    try {
        // 1. Login to get token
        console.log("Logging in as admin...");
        const loginRes = await axios.post('http://localhost:8080/auth/login', {
            email: 'admin@unios.com',
            password: 'admin123'
        });
        const token = loginRes.data.token || loginRes.data;
        console.log("Logged in. Token:", token.substring(0, 20) + "...");

        // 2. Try to delete batch 1
        console.log("Attempting to delete batch 1...");
        const deleteRes = await axios.delete('http://localhost:8080/batches/1', {
            headers: {
                Authorization: `Bearer ${token}`
            }
        });
        console.log("Delete success:", deleteRes.data);
    } catch (err) {
        console.error("Delete failed:");
        if (err.response) {
            console.error("Status:", err.response.status);
            console.error("Data:", err.response.data);
        } else {
            console.error(err.message);
        }
    }
}

testDelete();
