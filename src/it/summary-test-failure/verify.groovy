def console = new File(basedir, 'build.log').text
def run2 = console.substring(console.lastIndexOf('BUILD SUCCESS'))

assert run2.contains('BUILD FAILED in')
assert run2.contains('tests: 2 run, 1 failed')
assert run2.contains('TEST     CalcTest.addsNumbers  src/test/java/demo/CalcTest.java:11')
assert run2.contains('AssertionFailedError: expected: <5> but was: <4>')
assert run2.contains('Full log: target/claude/build.log')
// lifecycle noise stays out of the console...
assert !run2.contains('T E S T S')
assert !run2.contains('[INFO] Building calc')
// ...but is all in the full log, including Maven's own failure block
def log = new File(basedir, 'target/claude/build.log').text
assert log.contains('T E S T S')
assert log.contains('BUILD FAILURE')
def json = new groovy.json.JsonSlurper().parse(new File(basedir, 'target/claude/build-report.json'))
assert json.success == false
assert json.tests.run == 2 && json.tests.failed == 1
assert json.problems[0].kind == 'TEST'
assert json.problems[0].location == 'src/test/java/demo/CalcTest.java:11'
