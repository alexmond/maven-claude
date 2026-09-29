def console = new File(basedir, 'build.log').text

// Extension registered by run 1, but without CLAUDECODE it must not touch anything.
assert new File(basedir, '.mvn/extensions.xml').text.contains('claude-maven-plugin')
assert console.contains('T E S T S')
assert console.contains('Building jar')
assert !console.contains('BUILD OK in')
assert !new File(basedir, 'target/claude').exists()
