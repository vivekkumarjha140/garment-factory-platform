import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Modal } from './Modal'

describe('Modal', () => {
    it('is a labelled dialog with its content', () => {
        render(
            <Modal title="Adjust stock" onClose={() => {}}>
                <p>Body text</p>
            </Modal>,
        )

        expect(screen.getByRole('dialog', { name: 'Adjust stock' })).toBeInTheDocument()
        expect(screen.getByText('Body text')).toBeInTheDocument()
    })

    it('closes with the Close button and with Escape', async () => {
        const onClose = vi.fn()
        render(
            <Modal title="Adjust stock" onClose={onClose}>
                <p>Body text</p>
            </Modal>,
        )
        const user = userEvent.setup()

        await user.click(screen.getByRole('button', { name: 'Close' }))
        await user.keyboard('{Escape}')

        expect(onClose).toHaveBeenCalledTimes(2)
    })

    it('moves focus into the dialog and keeps Tab inside it', async () => {
        render(
            <Modal title="Adjust stock" onClose={() => {}}>
                <input aria-label="Quantity" />
            </Modal>,
        )
        const user = userEvent.setup()

        expect(screen.getByRole('button', { name: 'Close' })).toHaveFocus()
        await user.tab()
        expect(screen.getByLabelText('Quantity')).toHaveFocus()
        await user.tab()
        expect(screen.getByRole('button', { name: 'Close' })).toHaveFocus()
    })

    it('gives focus back to the button that opened it', async () => {
        const { rerender } = render(<button>Open</button>)
        const opener = screen.getByRole('button', { name: 'Open' })
        opener.focus()

        rerender(
            <>
                <button>Open</button>
                <Modal title="Dialog" onClose={() => {}}>
                    <p>Body</p>
                </Modal>
            </>,
        )
        expect(screen.getByRole('button', { name: 'Close' })).toHaveFocus()

        rerender(<button>Open</button>)
        expect(screen.getByRole('button', { name: 'Open' })).toHaveFocus()
    })
})