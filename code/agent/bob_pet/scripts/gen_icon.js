// Generate a 32x32 robot icon as PNG without any dependencies
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const W = 32, H = 32;

// Robot pixel art (32x32) - 1=dark, 2=primary, 3=eye, 4=accent, 0=transparent
const pixels = [
  '................................',
  '..............22................',
  '.............2222...............',
  '.............2332...............',
  '.............2222...............',
  '..............22................',
  '............222222..............',
  '............2332332.............',
  '............2222222.............',
  '............2442442.............',
  '............2222222.............',
  '...........222222222............',
  '...........224444422............',
  '...........224333422............',
  '...........224444422............',
  '...........222222222............',
  '..........22222222222...........',
  '..........22444444422...........',
  '..........22443334422...........',
  '..........22444444422...........',
  '..........22443334422...........',
  '..........22444444422...........',
  '..........22222222222...........',
  '...........222222222............',
  '...........22....22.............',
  '...........22....22.............',
  '...........44....44.............',
  '...........22....22.............',
  '...........22....22.............',
  '...........44....44.............',
  '................................',
  '................................',
];

const colors = {
  '0': [0, 0, 0, 0],         // transparent
  '1': [30, 58, 95, 255],     // dark
  '2': [59, 130, 246, 255],   // primary blue
  '3': [251, 191, 36, 255],   // eye yellow
  '4': [147, 197, 253, 255],  // light blue
};

function makePNG() {
  // Build raw RGBA data
  const rawData = Buffer.alloc((W * 4 + 1) * H);
  for (let y = 0; y < H; y++) {
    const rowStart = y * (W * 4 + 1);
    rawData[rowStart] = 0; // filter: none
    for (let x = 0; x < W; x++) {
      const ch = pixels[y]?.[x] || '.';
      const c = colors[ch === '.' ? '0' : ch] || colors['0'];
      const off = rowStart + 1 + x * 4;
      rawData[off] = c[0];
      rawData[off + 1] = c[1];
      rawData[off + 2] = c[2];
      rawData[off + 3] = c[3];
    }
  }

  const compressed = zlib.deflateSync(rawData);

  // PNG signature
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);

  function chunk(type, data) {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length, 0);
    const typeB = Buffer.from(type, 'ascii');
    const crcData = Buffer.concat([typeB, data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(crcData), 0);
    return Buffer.concat([len, typeB, data, crc]);
  }

  // IHDR
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(W, 0);
  ihdr.writeUInt32BE(H, 4);
  ihdr[8] = 8;  // bit depth
  ihdr[9] = 6;  // RGBA
  ihdr[10] = 0; // compression
  ihdr[11] = 0; // filter
  ihdr[12] = 0; // interlace

  const png = Buffer.concat([
    sig,
    chunk('IHDR', ihdr),
    chunk('IDAT', compressed),
    chunk('IEND', Buffer.alloc(0))
  ]);

  return png;
}

// CRC32
function crc32(buf) {
  let table = crc32.table;
  if (!table) {
    table = crc32.table = new Uint32Array(256);
    for (let i = 0; i < 256; i++) {
      let c = i;
      for (let j = 0; j < 8; j++) c = (c & 1) ? 0xEDB88320 ^ (c >>> 1) : (c >>> 1);
      table[i] = c;
    }
  }
  let crc = 0xFFFFFFFF;
  for (let i = 0; i < buf.length; i++) crc = table[(crc ^ buf[i]) & 0xFF] ^ (crc >>> 8);
  return (crc ^ 0xFFFFFFFF) >>> 0;
}

const outDir = path.join(__dirname, '..', 'assets');
if (!fs.existsSync(outDir)) fs.mkdirSync(outDir, { recursive: true });

const png = makePNG();
fs.writeFileSync(path.join(outDir, 'tray.png'), png);
console.log('Generated tray.png (' + png.length + ' bytes)');
