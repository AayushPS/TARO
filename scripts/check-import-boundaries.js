#!/usr/bin/env node
/*
Expected output format:
VIOLATION | <file> | imports | <imported path>
Exit code 0 when no violations are found, or 1 when at least one violation exists.
*/

const fs = require('node:fs')
const path = require('node:path')

const repoRoot = process.cwd()
const frontendRoot = path.join(repoRoot, 'taro-frontend')
const appDirs = ['maps-app', 'traffic-app']
const importPattern =
  /\b(?:import|export)\s+(?:[^'"`]*?\s+from\s+)?['"]([^'"`]+)['"]|\bimport\s*\(\s*['"]([^'"`]+)['"]\s*\)/g
const fileExtensions = ['.js', '.jsx', '.ts', '.tsx', '.mjs', '.cjs']
const skippedDirs = new Set(['node_modules', 'dist', '.git', 'coverage'])

function normalizePath(filePath) {
  return filePath.split(path.sep).join('/')
}

function fileExists(candidate) {
  try {
    return fs.statSync(candidate).isFile()
  } catch {
    return false
  }
}

function directoryExists(candidate) {
  try {
    return fs.statSync(candidate).isDirectory()
  } catch {
    return false
  }
}

function resolveImportPath(fromFile, specifier) {
  if (specifier.startsWith('/')) {
    return path.resolve(frontendRoot, `.${specifier}`)
  }

  const rawCandidate = path.resolve(path.dirname(fromFile), specifier)
  const candidates = [rawCandidate]

  for (const extension of fileExtensions) {
    candidates.push(`${rawCandidate}${extension}`)
  }

  for (const extension of fileExtensions) {
    candidates.push(path.join(rawCandidate, `index${extension}`))
  }

  for (const candidate of candidates) {
    if (fileExists(candidate)) {
      return candidate
    }
  }

  return rawCandidate
}

function collectFiles(rootDir, files) {
  if (!directoryExists(rootDir)) {
    return
  }

  const entries = fs.readdirSync(rootDir, { withFileTypes: true })
  for (const entry of entries) {
    if (entry.isDirectory()) {
      if (!skippedDirs.has(entry.name)) {
        collectFiles(path.join(rootDir, entry.name), files)
      }
      continue
    }

    if (fileExtensions.includes(path.extname(entry.name))) {
      files.push(path.join(rootDir, entry.name))
    }
  }
}

function owningApp(filePath) {
  const normalized = normalizePath(path.relative(frontendRoot, filePath))
  if (normalized.startsWith('maps-app/')) {
    return 'maps-app'
  }
  if (normalized.startsWith('traffic-app/')) {
    return 'traffic-app'
  }
  return null
}

const explicitAppRoots = appDirs
  .map((dirName) => path.join(frontendRoot, dirName))
  .filter(directoryExists)

const scanRoots =
  explicitAppRoots.length > 0
    ? explicitAppRoots
    : [path.join(frontendRoot, 'src')].filter(directoryExists)

const filesToScan = []
for (const scanRoot of scanRoots) {
  collectFiles(scanRoot, filesToScan)
}

const violations = []

for (const filePath of filesToScan) {
  const source = fs.readFileSync(filePath, 'utf8')
  const importerApp = owningApp(filePath)
  let match = importPattern.exec(source)

  while (match) {
    const specifier = match[1] ?? match[2]

    if (specifier.startsWith('.')
      || specifier.startsWith('/')
      || specifier.startsWith('taro-frontend/')) {
      const resolved = specifier.startsWith('taro-frontend/')
        ? path.resolve(repoRoot, specifier)
        : resolveImportPath(filePath, specifier)
      const normalizedResolved = normalizePath(path.resolve(resolved))
      const normalizedFrontendRoot = normalizePath(path.resolve(frontendRoot))
      const importedApp = owningApp(resolved)

      if (!normalizedResolved.startsWith(normalizedFrontendRoot)) {
        violations.push(
          `VIOLATION | ${normalizePath(path.relative(repoRoot, filePath))} | imports | ${specifier}`,
        )
      } else if (importerApp === 'maps-app' && importedApp === 'traffic-app') {
        violations.push(
          `VIOLATION | ${normalizePath(path.relative(repoRoot, filePath))} | imports | ${specifier}`,
        )
      } else if (importerApp === 'traffic-app' && importedApp === 'maps-app') {
        violations.push(
          `VIOLATION | ${normalizePath(path.relative(repoRoot, filePath))} | imports | ${specifier}`,
        )
      }
    }

    match = importPattern.exec(source)
  }

  importPattern.lastIndex = 0
}

if (violations.length > 0) {
  for (const violation of violations) {
    console.log(violation)
  }
  process.exit(1)
}
