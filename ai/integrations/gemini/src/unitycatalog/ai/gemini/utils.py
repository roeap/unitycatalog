from typing import List, Optional, Tuple

from google.generativeai import GenerativeModel, protos
from google.generativeai.types import ContentType, GenerateContentResponse, content_types


def get_function_calls(response: GenerateContentResponse) -> list[protos.FunctionCall]:
    """
    Extracts function calls from a given GenerateContentResponse.

    Args:
        response (GenerateContentResponse): The response object returned by the model's generation call.

    Returns:
        list[protos.FunctionCall]: A list of FunctionCall objects extracted from the single candidate's response.
    """

    candidates = response.candidates
    if len(candidates) != 1:
        raise ValueError(
            f"Invalid number of candidates: Automatic function calling only works with "
            f"1 candidate, but {len(candidates)} were provided."
        )
    parts = candidates[0].content.parts

    function_calls = [part.function_call for part in parts if part and "function_call" in part]
    return function_calls


def generate_tool_call_messages(
    *,
    model: GenerativeModel,
    response: GenerateContentResponse,
    conversation_history: list[ContentType],
) -> Tuple[List[ContentType], Optional[List[protos.Part]]]:
    """
    Generates and appends tool call messages to the conversation history based on the model response.

    Args:
        model (GenerativeModel): The model that produced the response. This model may have associated tools.
        response (GenerateContentResponse): The response generated by the model.
        conversation_history (list[ContentType]): A list of conversation messages leading up to the current point.
            This history will be modified to include new messages generated from the function calls.

    Returns:
        tuple:
            - Updated conversation_history (list[ContentType]): The modified conversation history including tool call results.
            - function_response_parts (list[protos.Part] | None): The parts generated by executing the function calls,
              or None if no function calls were made.
    """
    if model._tools is None:
        return conversation_history, None
    else:
        tools_lib = content_types.to_function_library(model._tools)

    function_calls = get_function_calls(response)

    updated_history = conversation_history.copy()
    updated_history.append(response.candidates[0].content)

    if function_calls == []:
        return updated_history, None

    function_response_parts: list[protos.Part] = []
    for fc in function_calls:
        fr = tools_lib(fc)
        if fr is None:
            raise ValueError(
                "Unexpected state: The function reference (fr) should never be None. It should only return None if the declaration "
                "is not callable, which is checked earlier in the code."
            )
        function_response_parts.append(fr)

    send = protos.Content(role="user", parts=function_response_parts)
    updated_history.append(send)

    return updated_history, function_response_parts
