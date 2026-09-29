def console = new File(basedir, 'build.log').text
def run2 = console.substring(console.lastIndexOf('BUILD SUCCESS'))

assert run2.contains('BUILD FAILED in')
assert run2.contains('3 modules: 1 ok, 1 failed, 1 skipped')
assert run2.contains('COMPILE  lib/src/main/java/lib/Price.java:6:16')
assert run2.contains('incompatible types: int cannot be converted to java.lang.String')
assert !run2.contains('COMPILATION ERROR')
assert !run2.contains('Reactor Summary')
def json = new groovy.json.JsonSlurper().parse(new File(basedir, 'target/claude/build-report.json'))
assert json.modules.failed == 1 && json.modules.skipped == 1
assert json.problems.size() == 1
assert json.problems[0].module == 'lib'
