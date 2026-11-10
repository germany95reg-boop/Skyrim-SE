"""v2.36: auto-check NIF content after multi-BSA metadata scan."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
r=lambda p:(root/p).read_text(encoding="utf-8")
ui=r("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
wf=r("../.github/workflows/android-quality-gates.yml")
gradle=r("app/build.gradle")
entry=r("app/src/main/java/dev/skyrimsemobile/launcher/BsaEntryInspector.java")
verifier=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4MatchedNifVerifier.java")
assert 'if(lastNifCandidates.isEmpty())' in ui
assert 'verifyFoundModels();' in ui
assert 'lastMultiBsaReport=full;' in ui
assert ui.index('lastMultiBsaReport=full;')<ui.index('verifyFoundModels();')
assert '"запускается автоматически..."' in ui
assert 'final long verification=++nifVerificationSequence;' in ui
assert 'verification==nifVerificationSequence' in ui
assert 'Проверка содержимого найденных NIF' in ui
assert 'BsaEntryInspector.inspectProfile(' in ui
assert 'BsaEntryInspector.inspectProfile(in,entry,archiveFlags)' in verifier
assert 'MAX_PACKED = 8 * 1024 * 1024' in entry
assert 'MAX_DECODED = 16 * 1024 * 1024' in entry
assert 'v236_nif_auto_contract_test.py' in wf
assert "versionCode 74" in gradle and "versionName '2.51.0'" in gradle
print("PASS v2.36 automatic bounded NIF content verification after Data scan")
