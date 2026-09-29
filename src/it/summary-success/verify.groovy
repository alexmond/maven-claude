def console = new File(basedir, 'build.log').text
def run2 = console.substring(console.lastIndexOf('BUILD SUCCESS'))

assert run2.contains('BUILD OK in')
assert run2.contains('tests: 2 passed')
assert !run2.contains('T E S T S')
assert !run2.contains('Building jar')
assert new File(basedir, 'target/claude/build.log').text.contains('T E S T S')
