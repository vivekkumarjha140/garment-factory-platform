import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useForm } from 'react-hook-form'
import { toApiError, userMessage, type ApiError } from '../../api/errors'
import { inventoryApi } from '../../api/inventory'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { SelectField } from '../../components/ui/SelectField'
import { TextField } from '../../components/ui/TextField'
import type { StockItem } from '../../types/inventory'
import { reportSubmitError } from './formHelpers'
import { inventoryKeys } from './queryKeys'
import { measuredCountSchema, unitCountSchema, type MeasuredCountFormValues, type UnitCountFormValues } from './schemas'
import { variantLabel } from './stockStatus'

interface CountFormProps {
    publicId: string
    onSaved: (message: string) => void
}

const COUNT_FIELDS = ['countedQuantity'] as const
const SEARCH_RESULTS = 8
const RAW_MATERIAL_OPTIONS = { size: 100 }

/** Count one finished-goods variant: find it, then enter the units counted on the shelf. */
export function FinishedGoodCountForm({ publicId, onSaved }: CountFormProps) {
    const queryClient = useQueryClient()
    const [draft, setDraft] = useState('')
    const [query, setQuery] = useState('')
    const [selected, setSelected] = useState<StockItem | null>(null)
    const [error, setError] = useState<ApiError | null>(null)

    const params = { q: query, page: 0, size: SEARCH_RESULTS }
    const found = useQuery({
        queryKey: inventoryKeys.stockList(params),
        queryFn: () => inventoryApi.listStock(params),
        enabled: query !== '',
    })

    const {
        register,
        handleSubmit,
        reset,
        setError: setFieldError,
        formState: { errors, isSubmitting },
    } = useForm<UnitCountFormValues>({ resolver: zodResolver(unitCountSchema), defaultValues: { countedQuantity: '' } })

    function handleFind(event: FormEvent) {
        event.preventDefault()
        setQuery(draft.trim())
    }

    async function onSubmit(values: UnitCountFormValues) {
        if (!selected) return
        setError(null)
        try {
            await inventoryApi.countFinishedGood(publicId, selected.variantId, Number(values.countedQuantity))
            await queryClient.invalidateQueries({ queryKey: inventoryKeys.audit(publicId) })
            const label = variantLabel(selected)
            reset()
            setSelected(null)
            onSaved(`Count saved for ${label}.`)
        } catch (caught) {
            reportSubmitError(caught, setFieldError, COUNT_FIELDS, setError)
        }
    }

    return (
        <section aria-labelledby="count-fg-title" className="space-y-3 rounded-md border border-slate-200 p-4 dark:border-slate-800">
            <h2 id="count-fg-title" className="text-lg font-semibold">
                Count finished goods
            </h2>
            {error && <Alert variant="error">{userMessage(error)}</Alert>}

            {!selected && (
                <>
                    <form onSubmit={handleFind} role="search" className="flex gap-2">
                        <label htmlFor="audit-variant-search" className="sr-only">
                            Find a variant (SKU or product name)
                        </label>
                        <input
                            id="audit-variant-search"
                            value={draft}
                            onChange={(event) => setDraft(event.target.value)}
                            placeholder="Find a variant (SKU or product name)"
                            className="block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm dark:border-slate-700 dark:bg-slate-950"
                        />
                        <Button type="submit" variant="secondary">
                            Find
                        </Button>
                    </form>
                    {found.isError && <Alert variant="error">{userMessage(toApiError(found.error))}</Alert>}
                    {found.data && found.data.items.length === 0 && <p className="text-sm text-slate-500">No stocked variant matches.</p>}
                    {found.data && found.data.items.length > 0 && (
                        <ul className="divide-y divide-slate-200 rounded-md border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
                            {found.data.items.map((item) => (
                                <li key={item.variantId} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                                    <span>
                                        {variantLabel(item)} <span className="text-slate-500">{item.sku}</span>
                                    </span>
                                    <Button variant="secondary" className="px-2 py-1 text-xs" aria-label={`Select ${variantLabel(item)}`} onClick={() => setSelected(item)}>
                                        Select
                                    </Button>
                                </li>
                            ))}
                        </ul>
                    )}
                </>
            )}

            {selected && (
                <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-3">
                    <p className="text-sm">
                        Counting: <span className="font-medium">{variantLabel(selected)}</span>
                    </p>
                    <TextField
                        label="Counted units"
                        inputMode="numeric"
                        autoComplete="off"
                        autoFocus
                        hint="Counting an item again replaces its earlier count."
                        error={errors.countedQuantity?.message}
                        {...register('countedQuantity')}
                    />
                    <div className="flex gap-2">
                        <Button type="submit" loading={isSubmitting}>
                            Save count
                        </Button>
                        <Button variant="secondary" onClick={() => setSelected(null)}>
                            Choose another
                        </Button>
                    </div>
                </form>
            )}
        </section>
    )
}

/** Count one raw material. Quantities can have up to 3 decimals. */
export function RawMaterialCountForm({ publicId, onSaved }: CountFormProps) {
    const queryClient = useQueryClient()
    const [rawMaterialId, setRawMaterialId] = useState('')
    const [selectError, setSelectError] = useState<string | null>(null)
    const [error, setError] = useState<ApiError | null>(null)

    const materials = useQuery({
        queryKey: inventoryKeys.rawMaterials(RAW_MATERIAL_OPTIONS),
        queryFn: () => inventoryApi.listRawMaterials(RAW_MATERIAL_OPTIONS),
    })

    const {
        register,
        handleSubmit,
        reset,
        setError: setFieldError,
        formState: { errors, isSubmitting },
    } = useForm<MeasuredCountFormValues>({ resolver: zodResolver(measuredCountSchema), defaultValues: { countedQuantity: '' } })

    async function onSubmit(values: MeasuredCountFormValues) {
        if (rawMaterialId === '') {
            setSelectError('Choose a raw material')
            return
        }
        setSelectError(null)
        setError(null)
        try {
            await inventoryApi.countRawMaterial(publicId, Number(rawMaterialId), Number(values.countedQuantity))
            await queryClient.invalidateQueries({ queryKey: inventoryKeys.audit(publicId) })
            const name = materials.data?.items.find((item) => String(item.id) === rawMaterialId)?.name ?? 'raw material'
            reset()
            setRawMaterialId('')
            onSaved(`Count saved for ${name}.`)
        } catch (caught) {
            reportSubmitError(caught, setFieldError, COUNT_FIELDS, setError)
        }
    }

    return (
        <section aria-labelledby="count-raw-title" className="space-y-3 rounded-md border border-slate-200 p-4 dark:border-slate-800">
            <h2 id="count-raw-title" className="text-lg font-semibold">
                Count raw materials
            </h2>
            {error && <Alert variant="error">{userMessage(error)}</Alert>}
            {materials.isError && <Alert variant="error">{userMessage(toApiError(materials.error))}</Alert>}
            <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-3">
                <SelectField
                    label="Raw material"
                    value={rawMaterialId}
                    error={selectError ?? undefined}
                    onChange={(event) => setRawMaterialId(event.target.value)}
                >
                    <option value="">Choose a raw material</option>
                    {materials.data?.items.map((item) => (
                        <option key={item.id} value={item.id}>
                            {item.name} ({item.code})
                        </option>
                    ))}
                </SelectField>
                <TextField
                    label="Counted quantity"
                    inputMode="decimal"
                    autoComplete="off"
                    error={errors.countedQuantity?.message}
                    {...register('countedQuantity')}
                />
                <Button type="submit" loading={isSubmitting}>
                    Save raw material count
                </Button>
            </form>
        </section>
    )
}