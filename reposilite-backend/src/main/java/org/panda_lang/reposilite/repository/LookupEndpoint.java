/*
 * Copyright (c) 2020 Dzikoysk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.panda_lang.reposilite.repository;

import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.ContentType;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import org.apache.http.HttpStatus;
import org.panda_lang.reposilite.Reposilite;
import org.panda_lang.reposilite.ReposiliteContext;
import org.panda_lang.reposilite.auth.IAuthedHandler;
import org.panda_lang.reposilite.error.ErrorDto;
import org.panda_lang.reposilite.error.ResponseUtils;
import org.panda_lang.reposilite.resource.FrontendProvider;
import org.panda_lang.reposilite.utils.OutputUtils;
import org.panda_lang.utilities.commons.function.Result;

import java.io.IOException;
import java.util.function.BiConsumer;

public final class LookupEndpoint implements IAuthedHandler {
    private final FrontendProvider frontend;
    private final LookupService localLookup;
    private final BiConsumer<String, Exception> errorHandler;

    public LookupEndpoint(
            FrontendProvider frontendProvider,
            LookupService localLookup,
            BiConsumer<String, Exception> errorHandler) {
        this.frontend = frontendProvider;
        this.localLookup = localLookup;
        this.errorHandler = errorHandler;
    }

    @OpenApi(
        operationId = "repositoryLookup",
        summary = "Browse the contents of repositories",
        description = "The route may return various responses to properly handle Maven specification and frontend application using the same path.",
        tags = { "Repository" },
        pathParams = {
            @OpenApiParam(name = "*", description = "Artifact path qualifier", required = true, allowEmptyValue = true),
        },
        responses = {
            @OpenApiResponse(status = "200", description = "Input stream of requested file", content = {
                @OpenApiContent(type = ContentType.FORM_DATA_MULTIPART)
            }),
            @OpenApiResponse(
                status = "404",
                description = "Returns 404 (for Maven) with frontend (for user) as a response if requested resource is not located in the current repository"
            ),
        }
    )
    @Override
    public void handle(Context ctx, ReposiliteContext context) {
        Reposilite.getLogger().debug("LOOKUP " + context.uri() + " from " + context.address());

        if (context.filepath() == null) {
            ResponseUtils.errorResponse(ctx, new ErrorDto(HttpStatus.SC_BAD_REQUEST, "Invalid GAV path"));
            return;
        }

        Result<LookupResponse, ErrorDto> response = localLookup.findFile(context);

        handleResult(ctx, context, response);
    }

    private void handleResult(Context ctx, ReposiliteContext context, Result<LookupResponse, ErrorDto> result) {
        result
            .peek(response -> handleResult(ctx, context, response))
            .onError(error -> handleError(ctx, error));
    }

    private void handleResult(Context ctx, ReposiliteContext context, LookupResponse response) {
        response.getFileDetails().peek(details -> {
            if (details.getContentLength() > 0) {
                ctx.res.setContentLengthLong(details.getContentLength());
            }

            if (response.isAttachment()) {
                ctx.res.setHeader("Content-Disposition", "attachment; filename=\"" + details.getName() + "\"");
            }
        });

        response.getContentType().peek(ctx.res::setContentType);
        response.getValue().peek(ctx::result);

        context.result().peek(result -> {
            try {
                if (OutputUtils.isProbablyOpen(ctx.res.getOutputStream())) {
                    result.accept(ctx.res.getOutputStream());
                }
            } catch (IOException exception) {
                errorHandler.accept(context.uri(), exception);
            }
        });
    }

    private void handleError(Context ctx, ErrorDto error) {
        if (error.getStatus() == HttpStatus.SC_MOVED_TEMPORARILY) {
            ctx.redirect(error.getMessage());
        } else {
            ctx.result(frontend.forMessage(error.getStatus(), error.getMessage()))
                .status(error.getStatus())
                .contentType("text/html")
                .res.setCharacterEncoding("UTF-8");
        }
    }
}
