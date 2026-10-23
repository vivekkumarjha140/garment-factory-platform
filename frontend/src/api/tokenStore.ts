/** The access token lives only in memory: it is gone on reload and unreachable from web storage. */
let accessToken: string | null = null

export const tokenStore = {
    get: (): string | null => accessToken,
    set: (token: string | null): void => {
        accessToken = token
    },
}