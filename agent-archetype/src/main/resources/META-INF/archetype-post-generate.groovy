// Renames the "gitignore" file shipped by the archetype to ".gitignore" in the
// generated project. Maven's DirectoryScanner DEFAULTEXCLUDES drop files matching
// .git*, .hg*, .svn, etc. when packaging archetype resources, so the file lives
// without the dot inside the archetype JAR and is renamed here at generation time.

def projectDir = new File(request.outputDirectory, request.artifactId)
def src = new File(projectDir, 'gitignore')
def dst = new File(projectDir, '.gitignore')

if (src.exists()) {
    if (!src.renameTo(dst)) {
        // Fallback: copy + delete (renameTo can fail across filesystems)
        dst.bytes = src.bytes
        src.delete()
    }
}
