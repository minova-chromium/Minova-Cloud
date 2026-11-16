import fs from 'node:fs/promises';
import path from 'node:path';
import {appVersion} from './release-info.mjs';

const token=process.env.GITHUB_TOKEN;
const repository=process.env.GITHUB_REPOSITORY;
if(!token||!/^minova-chromium\/Minova-Cloud$/i.test(repository||''))throw new Error('Run this workflow in the Minova Cloud repository.');
const headers={Authorization:`Bearer ${token}`,Accept:'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28','User-Agent':'Minova-Cloud-Releases'};
async function api(route,method='GET',body){
  const response=await fetch(`https://api.github.com/repos/${repository}${route}`,{method,headers:{...headers,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});
  if(response.status===404)return null;
  if(!response.ok)throw new Error(`GitHub ${method} failed (${response.status}).`);
  return response.status===204?null:response.json();
}
const {version,versionCode}=await appVersion();
const tag=`v${version}`;
const metadata=JSON.parse(await fs.readFile('releases/release.json','utf8'));
if(metadata.version!==version||metadata.versionCode!==versionCode)throw new Error('The APK metadata does not match the source version.');
const releases=await api('/releases?per_page=100');
let release=releases.find(item=>item.tag_name===tag);
if(release&&!release.draft){
  console.log(`${tag} is already published and will not be modified. Increase both Android version fields for the next release.`);
  process.exit(0);
}
const latest=await api('/releases/latest');
if(latest){
  const asset=latest.assets.find(item=>item.name==='release.json');
  if(asset){const response=await fetch(asset.browser_download_url);if(!response.ok)throw new Error('Could not verify the previous release version.');const previous=await response.json();if(versionCode<=previous.versionCode)throw new Error('Increase versionCode before preparing a new release.');}
}
const body=[`Minova Cloud ${version}`, '', '### Installation', 'Download **Minova-Cloud.apk** and install it over your existing app. Your account and settings are retained.', '', '### Included', '- Full-screen photo browsing with swipe, previous/next, pinch and double-tap zoom, fit, and adjacent-photo preloading.', '- Phone backups album inside Pictures, with checksum-verified legacy receipt recovery.', '- Built-in update checks, automatic Wi-Fi downloads, and in-app installation with Android approval.', '- Approved Minova Cloud logo with the original Minova M.', '- Files, Photos, and Videos with the Minova visual system.', '- Private sharing, queued uploads, and opt-in photo/video backup.', '- On this phone gallery and optional shared-file browsing.', '- Verified phone-space cleanup with separate in-app and Android confirmation (Android 11+).' , '', 'SHA-256 checksums and public app metadata are attached.'].join('\n');
if(!release)release=await api('/releases','POST',{tag_name:tag,target_commitish:process.env.GITHUB_SHA,name:`Minova Cloud ${version}`,body,draft:true,prerelease:false});
else release=await api(`/releases/${release.id}`,'PATCH',{tag_name:tag,target_commitish:process.env.GITHUB_SHA,name:`Minova Cloud ${version}`,body,draft:true,prerelease:false});
for(const name of ['Minova-Cloud.apk',`Minova-Cloud-${version}.apk`,'SHA256SUMS.txt','release.json']){
  const old=release.assets.find(asset=>asset.name===name);
  if(old)await api(`/releases/assets/${old.id}`,'DELETE');
  const bytes=await fs.readFile(path.join('releases',name));
  const response=await fetch(`https://uploads.github.com/repos/${repository}/releases/${release.id}/assets?name=${encodeURIComponent(name)}`,{method:'POST',headers:{...headers,'Content-Type':name.endsWith('.apk')?'application/vnd.android.package-archive':'application/octet-stream'},body:bytes});
  if(!response.ok)throw new Error(`Upload failed for ${name} (${response.status}).`);
}
if(process.env.PUBLISH_RELEASE==='true'){
  release=await api(`/releases/${release.id}`,'PATCH',{draft:false,make_latest:'true'});
  console.log(`Published ${tag}: ${release.html_url}`);
}else console.log(`Draft ready: ${release.html_url}`);
if(process.env.GITHUB_STEP_SUMMARY)await fs.appendFile(process.env.GITHUB_STEP_SUMMARY,`## Minova Cloud ${version}\n\n${release.draft?'Draft APK is ready for review. Publishing this draft switches the website to this version.':'Published APK is now the website’s latest download.'}\n\n[View releases](https://github.com/${repository}/releases)\n`);
