package com.tesoro.application.port.in;

import com.tesoro.application.command.ConvertCommand;
import com.tesoro.application.command.DepositCommand;
import com.tesoro.application.command.TransferCommand;
import com.tesoro.application.command.WithdrawCommand;
import com.tesoro.application.model.IdempotentResult;
import com.tesoro.application.model.MovementResult;

/** Money-moving operations. All of them are idempotent per (user, idempotency key). */
public interface WalletOperationsUseCase {

    IdempotentResult<MovementResult> deposit(DepositCommand command);

    IdempotentResult<MovementResult> withdraw(WithdrawCommand command);

    IdempotentResult<MovementResult> transfer(TransferCommand command);

    IdempotentResult<MovementResult> convert(ConvertCommand command);
}
