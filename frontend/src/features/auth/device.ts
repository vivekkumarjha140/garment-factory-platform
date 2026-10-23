/** A short, human label for a session ("Chrome on Windows") from the stored User-Agent header. */
export function describeUserAgent(userAgent: string | null): string {
    if (!userAgent) return 'Unknown device'
    const browser = detectBrowser(userAgent)
    const os = detectOs(userAgent)
    if (!browser && !os) return 'Unknown device'
    return [browser ?? 'Unknown browser', os ? `on ${os}` : null].filter(Boolean).join(' ')
}

// The order matters: Edge and Opera also say "Chrome", and Chrome also says "Safari".
function detectBrowser(ua: string): string | null {
    if (/Edg\//.test(ua)) return 'Edge'
    if (/OPR\//.test(ua)) return 'Opera'
    if (/Firefox\//.test(ua)) return 'Firefox'
    if (/Chrome\//.test(ua)) return 'Chrome'
    if (/Safari\//.test(ua)) return 'Safari'
    return null
}

// Android also says "Linux", and iPhone also says "Mac OS X".
function detectOs(ua: string): string | null {
    if (/Windows/.test(ua)) return 'Windows'
    if (/Android/.test(ua)) return 'Android'
    if (/iPhone|iPad|iPod/.test(ua)) return 'iOS'
    if (/Mac OS X|Macintosh/.test(ua)) return 'macOS'
    if (/Linux/.test(ua)) return 'Linux'
    return null
}