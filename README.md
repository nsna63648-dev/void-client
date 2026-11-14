# VOID CLIENT (Fabric 1.21.11, Java 21)

Build:   ./gradlew build   -> build/libs/voidclient-1.0.0.jar  (ใส่ในโฟลเดอร์ mods)
Dev run: ./gradlew runClient

ก่อน build: เปิด https://fabricmc.net/develop เลือก 1.21.11 แล้วคัดลอกเลข yarn / loader / fabric api / loom
มาใส่ใน gradle.properties ให้ตรงล่าสุด (ถ้าไม่มี gradle wrapper ให้ copy จาก template ของ Fabric หรือรัน `gradle wrapper`)

เปิดเมนู: RIGHT SHIFT หรือปุ่ม VOID CLIENT บน Main Menu
ตั้งค่า: config/voidclient.json

## ได้ .jar โดยไม่ต้องลงอะไรในเครื่อง (GitHub Actions)
1. สมัคร github.com แล้วสร้าง repository ใหม่ (private ก็ได้)
2. อัปโหลดไฟล์ทั้งหมดในโฟลเดอร์นี้ (รวมโฟลเดอร์ .github)
3. แท็บ Actions -> Build VOID CLIENT -> รอประมาณ 3-5 นาที
4. เปิดงานที่เสร็จ -> ดาวน์โหลด Artifact "voidclient-jar" -> แตก zip จะได้ voidclient-1.0.0.jar
5. ใส่ในโฟลเดอร์ mods พร้อม Fabric Loader 1.21.11 + Fabric API
