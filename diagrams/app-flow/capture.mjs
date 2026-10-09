import { chromium } from 'playwright';
const b = await chromium.launch({executablePath:'/Users/kenjji/Library/Caches/ms-playwright/chromium_headless_shell-1228/chrome-headless-shell-mac-arm64/chrome-headless-shell'});
const p = await b.newPage({ viewport:{width:1000,height:1100}, deviceScaleFactor:2 });
await p.addInitScript(()=>{ const orig=performance.now.bind(performance); window.__fz=0; performance.now=()=> window.__fz!==null ? window.__fz : orig(); window.__orig=orig; });
await p.goto('file:///Users/kenjji/Documents/SDA%20Project/design/The%201975%20Ticket%20App%201a%20(offline).html');
await p.waitForTimeout(3000);
const ids = await p.$eval('select', s=>[...s.options].map(o=>o.value));
const phone = await p.evaluateHandle(()=>document.querySelector('select').parentElement.nextElementSibling);
for (const id of ids) {
  await p.selectOption('select', id);
  await p.waitForTimeout(id==='06c'||id==='08b'?300:450);
  await phone.asElement().screenshot({path: '/private/tmp/claude-501/-Users-kenjji-Documents-SDA-Project/319ecebe-1dc2-4138-8f91-84d564b557d9/scratchpad/shot/png/'+id+'.jpg', type:'jpeg', quality:82});
}
console.log(ids.join(' '));
await b.close();
