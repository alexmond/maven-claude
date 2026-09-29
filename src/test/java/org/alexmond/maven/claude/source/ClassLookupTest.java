package org.alexmond.maven.claude.source;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassLookupTest {

	@TempDir
	Path dir;

	@Test
	void candidatesTryPackagesThenNesting() {
		assertEquals(List.of("a/b/C/D.class", "a/b/C$D.class", "a/b$C$D.class"),
				ClassLookup.entryCandidates("a.b.C.D"));
		assertEquals(List.of("a/b/C$D.class", "a/b$C$D.class"), ClassLookup.entryCandidates("a.b.C$D"));
		assertEquals(List.of("a/B.class"), ClassLookup.entryCandidates("a/B.java"));
		assertEquals(List.of("Foo.class"), ClassLookup.entryCandidates("Foo"));
	}

	@Test
	void namesDerivedFromEntries() {
		assertEquals("a/b/C.java", ClassLookup.sourceEntry("a/b/C$D.class"));
		assertEquals("a/b/C.java", ClassLookup.sourceEntry("a/b/C.class"));
		assertEquals("a.b.C$D", ClassLookup.binaryName("a/b/C$D.class"));
		assertTrue(ClassLookup.isSimpleName("Foo"));
		assertFalse(ClassLookup.isSimpleName("a.Foo"));
	}

	@Test
	void findsQualifiedNestedAndSimpleNamesInJar() throws IOException {
		Path jar = zip("lib.jar", "a/b/Foo.class", "a/b/Foo$Bar.class", "c/Foo.class", "META-INF/versions/9/Foo.class");
		assertEquals(List.of("a/b/Foo.class"), ClassLookup.find(jar, "a.b.Foo"));
		assertEquals(List.of("a/b/Foo$Bar.class"), ClassLookup.find(jar, "a.b.Foo.Bar"));
		assertEquals(List.of("a/b/Foo.class", "c/Foo.class"), ClassLookup.find(jar, "Foo"));
		assertEquals(List.of(), ClassLookup.find(jar, "a.b.Missing"));
		assertEquals(List.of(), ClassLookup.find(this.dir.resolve("absent.jar"), "Foo"));
	}

	@Test
	void findsInClassesDirectory() throws IOException {
		Path classes = this.dir.resolve("classes");
		Files.createDirectories(classes.resolve("a/b"));
		Files.writeString(classes.resolve("a/b/Foo.class"), "");
		assertEquals(List.of("a/b/Foo.class"), ClassLookup.find(classes, "a.b.Foo"));
		assertEquals(List.of("a/b/Foo.class"), ClassLookup.find(classes, "Foo"));
		assertEquals(List.of(), ClassLookup.find(classes, "a.b.Nope"));
	}

	@Test
	void extractsJavaOrKotlinSource() throws IOException {
		Path sources = zip("lib-sources.jar", "a/b/Foo.java", "k/Bar.kt");
		Path out = this.dir.resolve("out");
		Optional<Path> foo = ClassLookup.extract(sources, "", "a/b/Foo.java", out);
		assertEquals(out.resolve("a/b/Foo.java"), foo.orElseThrow());
		assertEquals("content of a/b/Foo.java", Files.readString(foo.get()));
		assertEquals(out.resolve("k/Bar.kt"), ClassLookup.extract(sources, "", "k/Bar.java", out).orElseThrow());
		assertTrue(ClassLookup.extract(sources, "", "x/Nope.java", out).isEmpty());
	}

	@Test
	void extractsWithArchivePrefix() throws IOException {
		Path srcZip = zip("src.zip", "java.base/java/util/List.java");
		Path out = this.dir.resolve("jdk");
		assertTrue(ClassLookup.extract(srcZip, "java.base/", "java/util/List.java", out).isPresent());
		assertTrue(Files.isRegularFile(out.resolve("java/util/List.java")));
	}

	@Test
	void unpackAllSkipsMetaInfAndRefusesZipSlip() throws IOException {
		Path sources = zip("ok-sources.jar", "a/A.java", "a/B.java", "META-INF/MANIFEST.MF");
		assertEquals(2, ClassLookup.unpackAll(sources, this.dir.resolve("all")));
		Path evil = zip("evil-sources.jar", "../escape.java");
		assertThrows(IOException.class, () -> ClassLookup.unpackAll(evil, this.dir.resolve("evil")));
	}

	@Test
	void jdkClassesMapToTheirModule() {
		assertEquals(Optional.of("java.base"), ClassLookup.jdkModule("java.util.List"));
		assertEquals(Optional.of("java.sql"), ClassLookup.jdkModule("java.sql.Connection"));
		assertTrue(ClassLookup.jdkModule("com.example.Nope").isEmpty());
		assertTrue(ClassLookup.jdkModule("NoPackage").isEmpty());
	}

	@Test
	void javapPrintsSignatures() throws IOException {
		Path classes = this.dir.resolve("javap");
		Files.createDirectories(classes.resolve("org/alexmond/maven/claude/source"));
		try (var in = getClass().getResourceAsStream("ClassLookup.class")) {
			if (in == null) {
				return;
			}
			Files.copy(in, classes.resolve("org/alexmond/maven/claude/source/ClassLookup.class"));
		}
		Optional<String> out = ClassLookup.javap(classes, "org.alexmond.maven.claude.source.ClassLookup");
		out.ifPresent((text) -> assertTrue(text.contains("entryCandidates"), text));
		assertTrue(ClassLookup.javap(classes, "no.such.Type").isEmpty());
	}

	private Path zip(String name, String... entries) throws IOException {
		Path file = this.dir.resolve(name);
		try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
			for (String entry : entries) {
				zip.putNextEntry(new ZipEntry(entry));
				zip.write(("content of " + entry).getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		}
		return file;
	}

}
