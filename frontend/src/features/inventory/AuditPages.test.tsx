import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/errors'
import { inventoryApi } from '../../api/inventory'
import { renderWithProviders } from '../../test/utils'
import type { AuditDetail, AuditLine, AuditSummary, PageResponse, StockItem } from '../../types/inventory'
import { Permission } from '../auth/permissions'
import { AuditDetailPage } from './AuditDetailPage'
import { AuditListPage } from './AuditListPage'

vi.mock('../../api/inventory', () => ({
    inventoryApi: {
        listAudits: vi.fn(),
        startAudit: vi.fn(),
        getAudit: vi.fn(),
        countFinishedGood: vi.fn(),
        countRawMaterial: vi.fn(),
        completeAudit: vi.fn(),
        cancelAudit: vi.fn(),
        listStock: vi.fn(),
        listRawMaterials: vi.fn(),
    },
}))

const listAudits = vi.mocked(inventoryApi.listAudits)
const startAudit = vi.mocked(inventoryApi.startAudit)
const getAudit = vi.mocked(inventoryApi.getAudit)
const countFinishedGood = vi.mocked(inventoryApi.countFinishedGood)
const countRawMaterial = vi.mocked(inventoryApi.countRawMaterial)
const completeAudit = vi.mocked(inventoryApi.completeAudit)
const cancelAudit = vi.mocked(inventoryApi.cancelAudit)
const listStock = vi.mocked(inventoryApi.listStock)
const listRawMaterials = vi.mocked(inventoryApi.listRawMaterials)

const OPEN: AuditSummary = {
    publicId: 'aud1',
    auditNumber: 'AUD-20261020-ABC123',
    status: 'IN_PROGRESS',
    notes: 'Q4 count',
    startedByUserId: 1,
    startedAt: '2026-10-20T09:00:00Z',
    completedAt: null,
}
const DONE: AuditSummary = { ...OPEN, publicId: 'aud0', auditNumber: 'AUD-20261001-ZZZ999', status: 'COMPLETED', completedAt: '2026-10-01T12:00:00Z' }

const SHORT: AuditLine = {
    id: 1,
    itemType: 'FINISHED_GOOD',
    variantId: 7,
    rawMaterialId: null,
    itemName: 'Round Neck Tee (M / Red)',
    sku: 'TEE-M-RED',
    systemQuantity: 10,
    countedQuantity: 8,
    variance: -2,
    adjustmentMovementId: null,
}
const MATCHING: AuditLine = { ...SHORT, id: 2, variantId: 8, itemName: 'Hoodie (L / Black)', sku: 'HOOD-L-BLK', systemQuantity: 5, countedQuantity: 5, variance: 0 }
const EXTRA_RAW: AuditLine = {
    id: 3,
    itemType: 'RAW_MATERIAL',
    variantId: null,
    rawMaterialId: 3,
    itemName: 'Cotton Jersey',
    sku: 'FAB-JERSEY',
    systemQuantity: 12.5,
    countedQuantity: 14,
    variance: 1.5,
    adjustmentMovementId: null,
}

const detail = (audit: AuditSummary, lines: AuditLine[]): AuditDetail => ({
    audit,
    lines,
    linesWithVariance: lines.filter((line) => line.variance !== 0).length,
})
const page = <T,>(items: T[]): PageResponse<T> => ({ items, page: 0, size: 20, totalElements: items.length, totalPages: 1 })

const staff = (...permissions: string[]) => ({
    auth: {
        status: 'authenticated' as const,
        user: { publicId: 'u1', email: 'wanda@example.com', fullName: 'Wanda', roles: ['WAREHOUSE_MANAGER'], permissions },
    },
})
const AUDITOR = staff(Permission.STOCK_VIEW, Permission.INVENTORY_AUDIT)

function renderDetail() {
    return renderWithProviders(
        <Routes>
            <Route path="/admin/inventory/audits/:publicId" element={<AuditDetailPage />} />
        </Routes>,
        { state: AUDITOR, route: '/admin/inventory/audits/aud1' },
    )
}

beforeEach(() => {
    vi.clearAllMocks()
    listAudits.mockResolvedValue(page([OPEN, DONE]))
    getAudit.mockResolvedValue(detail(OPEN, [SHORT, MATCHING, EXTRA_RAW]))
    listStock.mockResolvedValue(page<StockItem>([]))
    listRawMaterials.mockResolvedValue(page([]))
})

describe('AuditListPage', () => {
    it('lists audits with links to their detail pages and a status badge', async () => {
        renderWithProviders(<AuditListPage />, { state: AUDITOR })

        const link = await screen.findByRole('link', { name: 'AUD-20261020-ABC123' })
        expect(link).toHaveAttribute('href', '/admin/inventory/audits/aud1')
        expect(screen.getAllByText('In progress').length).toBeGreaterThan(0)
        expect(screen.getAllByText('Q4 count')).toHaveLength(2)
    })

    it('starts a new audit and opens it', async () => {
        startAudit.mockResolvedValue(OPEN)
        renderWithProviders(
            <Routes>
                <Route path="/" element={<AuditListPage />} />
                <Route path="/admin/inventory/audits/:publicId" element={<p>Audit detail page</p>} />
            </Routes>,
            { state: AUDITOR },
        )
        const user = userEvent.setup()

        await user.type(await screen.findByLabelText('Notes (optional)'), 'Q4 count')
        await user.click(screen.getByRole('button', { name: 'Start new audit' }))

        await waitFor(() => expect(startAudit).toHaveBeenCalledWith('Q4 count'))
        expect(await screen.findByText('Audit detail page')).toBeInTheDocument()
    })

    it('starts an audit without notes', async () => {
        startAudit.mockResolvedValue(OPEN)
        renderWithProviders(<AuditListPage />, { state: AUDITOR })

        await userEvent.setup().click(await screen.findByRole('button', { name: 'Start new audit' }))

        await waitFor(() => expect(startAudit).toHaveBeenCalledWith(undefined))
    })

    it('filters by status', async () => {
        renderWithProviders(<AuditListPage />, { state: AUDITOR })
        await screen.findByRole('link', { name: 'AUD-20261020-ABC123' })

        await userEvent.setup().selectOptions(screen.getByLabelText('Status'), 'COMPLETED')

        await waitFor(() => expect(listAudits).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'COMPLETED' })))
    })
})

describe('AuditDetailPage', () => {
    it('shows the counted items with their variances', async () => {
        renderDetail()

        expect(await screen.findByRole('heading', { name: 'AUD-20261020-ABC123' })).toBeInTheDocument()
        expect(screen.getByText('Round Neck Tee (M / Red)')).toBeInTheDocument()
        expect(screen.getByText('-2')).toBeInTheDocument()
        expect(screen.getByText('+1.5')).toBeInTheDocument()
        expect(screen.getByText('3 items counted, 2 variances.')).toBeInTheDocument()
    })

    it('applies variances only after an explicit confirmation', async () => {
        completeAudit.mockResolvedValue({ audit: { ...OPEN, status: 'COMPLETED' }, adjustmentsApplied: 2 })
        renderDetail()
        const user = userEvent.setup()

        await user.click(await screen.findByRole('button', { name: 'Complete and apply variances' }))
        expect(completeAudit).not.toHaveBeenCalled()
        expect(screen.getByText(/This changes stock for 2 items with a variance/)).toBeInTheDocument()
        await user.click(screen.getByRole('button', { name: 'Yes, apply variances' }))

        await waitFor(() => expect(completeAudit).toHaveBeenCalledWith('aud1', true))
        expect(await screen.findByText('Audit completed. 2 adjustments applied.')).toBeInTheDocument()
    })

    it('can complete without touching stock', async () => {
        completeAudit.mockResolvedValue({ audit: { ...OPEN, status: 'COMPLETED' }, adjustmentsApplied: 0 })
        renderDetail()

        await userEvent.setup().click(await screen.findByRole('button', { name: 'Complete without changing stock' }))

        await waitFor(() => expect(completeAudit).toHaveBeenCalledWith('aud1', false))
        expect(await screen.findByText('Audit completed. 0 adjustments applied.')).toBeInTheDocument()
    })

    it('shows the server message when applying the variances is refused', async () => {
        completeAudit.mockRejectedValue(new ApiError(409, 'INSUFFICIENT_STOCK', 'Adjustment of -8 would reduce on-hand (10) below reserved (6)'))
        renderDetail()
        const user = userEvent.setup()

        await user.click(await screen.findByRole('button', { name: 'Complete and apply variances' }))
        await user.click(screen.getByRole('button', { name: 'Yes, apply variances' }))

        expect(await screen.findByRole('alert')).toHaveTextContent('below reserved (6)')
    })

    it('cancels an audit only after confirming', async () => {
        cancelAudit.mockResolvedValue({ ...OPEN, status: 'CANCELLED' })
        renderDetail()
        const user = userEvent.setup()

        await user.click(await screen.findByRole('button', { name: 'Cancel audit' }))
        expect(cancelAudit).not.toHaveBeenCalled()
        await user.click(screen.getByRole('button', { name: 'Yes, cancel audit' }))

        await waitFor(() => expect(cancelAudit).toHaveBeenCalledWith('aud1'))
        expect(await screen.findByText('Audit cancelled. No stock was changed.')).toBeInTheDocument()
    })

    it('does not let an empty audit be completed', async () => {
        getAudit.mockResolvedValue(detail(OPEN, []))
        renderDetail()

        expect(await screen.findByRole('button', { name: 'Complete and apply variances' })).toBeDisabled()
        expect(screen.getByRole('button', { name: 'Complete without changing stock' })).toBeDisabled()
        expect(screen.getByText('Nothing has been counted yet.')).toBeInTheDocument()
    })

    it('shows a finished audit read-only', async () => {
        getAudit.mockResolvedValue(detail(DONE, [SHORT]))
        renderDetail()

        expect(await screen.findByRole('heading', { name: 'AUD-20261001-ZZZ999' })).toBeInTheDocument()
        expect(screen.queryByRole('button', { name: 'Complete and apply variances' })).not.toBeInTheDocument()
        expect(screen.queryByText('Count finished goods')).not.toBeInTheDocument()
    })

    it('counts a finished-goods variant found by search', async () => {
        listStock.mockResolvedValue(
            page<StockItem>([
                {
                    variantId: 7,
                    sku: 'TEE-M-RED',
                    productName: 'Round Neck Tee',
                    size: 'M',
                    colorName: 'Red',
                    variantStatus: 'ACTIVE',
                    publicId: 's7',
                    onHand: 10,
                    reserved: 0,
                    damaged: 0,
                    available: 10,
                    lowStockThreshold: 5,
                    lowStock: false,
                    version: 1,
                    updatedAt: '2026-10-20T10:00:00Z',
                },
            ]),
        )
        countFinishedGood.mockResolvedValue(SHORT)
        renderDetail()
        const user = userEvent.setup()

        await user.type(await screen.findByLabelText('Find a variant (SKU or product name)'), 'tee')
        await user.click(screen.getByRole('button', { name: 'Find' }))
        await user.click(await screen.findByRole('button', { name: 'Select Round Neck Tee (M / Red)' }))
        await user.type(screen.getByLabelText('Counted units'), '8')
        await user.click(screen.getByRole('button', { name: 'Save count' }))

        await waitFor(() => expect(countFinishedGood).toHaveBeenCalledWith('aud1', 7, 8))
        expect(await screen.findByText('Count saved for Round Neck Tee (M / Red).')).toBeInTheDocument()
    })

    it('does not accept a fractional count for finished goods', async () => {
        listStock.mockResolvedValue(
            page<StockItem>([
                {
                    variantId: 7,
                    sku: 'TEE-M-RED',
                    productName: 'Round Neck Tee',
                    size: 'M',
                    colorName: 'Red',
                    variantStatus: 'ACTIVE',
                    publicId: 's7',
                    onHand: 10,
                    reserved: 0,
                    damaged: 0,
                    available: 10,
                    lowStockThreshold: 5,
                    lowStock: false,
                    version: 1,
                    updatedAt: '2026-10-20T10:00:00Z',
                },
            ]),
        )
        renderDetail()
        const user = userEvent.setup()

        await user.type(await screen.findByLabelText('Find a variant (SKU or product name)'), 'tee')
        await user.click(screen.getByRole('button', { name: 'Find' }))
        await user.click(await screen.findByRole('button', { name: 'Select Round Neck Tee (M / Red)' }))
        await user.type(screen.getByLabelText('Counted units'), '8.5')
        await user.click(screen.getByRole('button', { name: 'Save count' }))

        expect(await screen.findByText('Enter the counted units as a whole number, 0 or more')).toBeInTheDocument()
        expect(countFinishedGood).not.toHaveBeenCalled()
    })

    it('counts a raw material with decimals', async () => {
        listRawMaterials.mockResolvedValue(
            page([
                {
                    id: 3,
                    publicId: 'rm3',
                    code: 'FAB-JERSEY',
                    name: 'Cotton Jersey',
                    category: 'FABRIC',
                    uom: 'METER',
                    onHand: 12.5,
                    reserved: 0,
                    available: 12.5,
                    lowStockThreshold: 5,
                    lowStock: false,
                    unitCost: 180,
                    active: true,
                    version: 1,
                },
            ]),
        )
        countRawMaterial.mockResolvedValue(EXTRA_RAW)
        renderDetail()
        const user = userEvent.setup()

        await user.selectOptions(await screen.findByLabelText('Raw material'), await screen.findByRole('option', { name: 'Cotton Jersey (FAB-JERSEY)' }))
        await user.type(screen.getByLabelText('Counted quantity'), '14.5')
        await user.click(screen.getByRole('button', { name: 'Save raw material count' }))

        await waitFor(() => expect(countRawMaterial).toHaveBeenCalledWith('aud1', 3, 14.5))
        expect(await screen.findByText('Count saved for Cotton Jersey.')).toBeInTheDocument()
    })

    it('asks for a raw material before saving a count', async () => {
        renderDetail()
        const user = userEvent.setup()

        await user.type(await screen.findByLabelText('Counted quantity'), '3')
        await user.click(screen.getByRole('button', { name: 'Save raw material count' }))

        expect(await screen.findByText('Choose a raw material', { selector: 'p' })).toBeInTheDocument()
        expect(countRawMaterial).not.toHaveBeenCalled()
    })

    it('says so when the audit cannot be loaded', async () => {
        getAudit.mockRejectedValue(new ApiError(404, 'STOCK_NOT_FOUND', 'Inventory audit not found: aud1'))
        renderDetail()

        expect(await screen.findByRole('alert')).toHaveTextContent('Inventory audit not found')
        expect(within(screen.getByRole('alert')).getByRole('button', { name: 'Retry' })).toBeInTheDocument()
    })
})