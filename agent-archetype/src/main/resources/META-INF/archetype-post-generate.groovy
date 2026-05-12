// ─────────────────────────────────────────────────────────────────────────
// archetype-post-generate.groovy — runs once after Maven materialises the
// archetype-resources into the user's target directory.
//
// Responsibilities:
//   1. Rename the "gitignore" resource to ".gitignore" — Maven's
//      DirectoryScanner DEFAULTEXCLUDES drop ".git*" entries, so the file
//      ships without the leading dot inside the archetype JAR.
//   2. Sanitize ${agentName} into a valid Java class identifier
//      (PascalCase, alpha-numeric only). When the user passes a value
//      that isn't a legal Java identifier (e.g. "agent-example-news")
//      the generated SpringBootApplication / test class names contain
//      hyphens and the project won't compile. We fix that here by
//      renaming the .java files AND patching their contents.
// ─────────────────────────────────────────────────────────────────────────

def projectDir = new File(request.outputDirectory, request.artifactId)

// ── 1. Rename gitignore → .gitignore ────────────────────────────────────
def src = new File(projectDir, 'gitignore')
def dst = new File(projectDir, '.gitignore')
if (src.exists()) {
    if (!src.renameTo(dst)) {
        // renameTo can fail across filesystems — fall back to copy + delete
        dst.bytes = src.bytes
        src.delete()
    }
}

// ── 2. Sanitize agentName into a Java identifier ────────────────────────
def rawAgentName = request.properties.get('agentName')
if (rawAgentName == null || rawAgentName.trim().isEmpty()) {
    return
}

String sanitized = toPascalCase(rawAgentName)
if (sanitized == rawAgentName) {
    return // already a valid identifier — nothing to do
}

println "[archetype] agentName '${rawAgentName}' contains characters " +
        "that are not valid in a Java identifier; using '${sanitized}' " +
        "as the SpringBootApplication class name."

String packagePath = (request.properties.get('package') ?: 'com.example').replace('.', '/')
def javaSourceDir = new File(projectDir, 'src/main/java/' + packagePath)
def javaTestDir   = new File(projectDir, 'src/test/java/' + packagePath)

renameAndPatchJavaClass(javaSourceDir, rawAgentName + 'Application', sanitized + 'Application')
renameAndPatchJavaClass(javaTestDir,   rawAgentName + 'ApplicationTest', sanitized + 'ApplicationTest')

// Patch the README.md (and any other top-level templated file) so the
// run instructions reference the correct class name.
['README.md'].each { name ->
    def f = new File(projectDir, name)
    if (f.exists()) {
        String text = f.text
        String patched = text.replace(rawAgentName + 'Application', sanitized + 'Application')
        if (patched != text) {
            f.text = patched
        }
    }
}

// ── helpers ─────────────────────────────────────────────────────────────

/** Convert an arbitrary user-supplied name into a Java-safe PascalCase identifier. */
static String toPascalCase(String raw) {
    def parts = raw.split(/[^A-Za-z0-9]+/).findAll { it }
    if (parts.empty) {
        return 'Agent'
    }
    StringBuilder sb = new StringBuilder()
    for (String p : parts) {
        sb.append(Character.toUpperCase(p.charAt(0)))
        if (p.length() > 1) {
            sb.append(p.substring(1))
        }
    }
    // Java identifiers cannot start with a digit
    if (Character.isDigit(sb.charAt(0))) {
        sb.insert(0, 'Agent')
    }
    return sb.toString()
}

static void renameAndPatchJavaClass(File dir, String oldBase, String newBase) {
    if (dir == null || !dir.exists()) {
        return
    }
    def oldFile = new File(dir, oldBase + '.java')
    def newFile = new File(dir, newBase + '.java')
    if (!oldFile.exists()) {
        return
    }
    if (!oldFile.renameTo(newFile)) {
        newFile.bytes = oldFile.bytes
        oldFile.delete()
    }
    // Replace every occurrence inside the class body — the template uses the
    // full "${agentName}Application" string both as the type name and in the
    // `SpringApplication.run(...)` call.
    newFile.text = newFile.text.replace(oldBase, newBase)
}
