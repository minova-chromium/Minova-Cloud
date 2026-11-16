import fs from 'node:fs/promises';
import path from 'node:path';
import {createHash} from 'node:crypto';

export async function appVersion() {
  const configuration=await fs.readFile('app/build.gradle.kts','utf8');
  const version=/versionName\s*=\s*"([^"]+)"/.exec(configuration)?.[1];
  const versionCode=Number(/versionCode\s*=\s*(\d+)/.exec(configuration)?.[1]);
  if(!/^\d+\.\d+\.\d+$/.test(version||'')||!Number.isSafeInteger(versionCode)||versionCode<1)throw new Error('Set a valid versionName and versionCode before building.');
  if(process.env.GITHUB_REF_TYPE==='tag' && process.env.GITHUB_REF_NAME!==`v${version}`)throw new Error('The tag must match the Android versionName.');
  return {version,versionCode};
}
if(process.argv[2]==='validate'){console.log(await appVersion());}
if(process.argv[2]==='package'){
  const {version,versionCode}=await appVersion();
  const certificateSha256=(await fs.readFile('signing-certificate.sha256','utf8')).trim();
  const bytes=await fs.readFile('app/build/outputs/apk/release/app-release.apk');
  const sha256=createHash('sha256').update(bytes).digest('hex');
  await fs.mkdir('releases',{recursive:true});
  for(const name of ['Minova-Cloud.apk',`Minova-Cloud-${version}.apk`])await fs.writeFile(path.join('releases',name),bytes);
  await fs.writeFile('releases/release.json',JSON.stringify({version,versionCode,package:'com.minova.cloud',sha256,certificateSha256,file:'Minova-Cloud.apk'},null,2)+'\n');
  await fs.writeFile('releases/SHA256SUMS.txt',`${sha256}  Minova-Cloud.apk\n${sha256}  Minova-Cloud-${version}.apk\n`);
  console.log(`Packaged Minova Cloud ${version}`);
}
